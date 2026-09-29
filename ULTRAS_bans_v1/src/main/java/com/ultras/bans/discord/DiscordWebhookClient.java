package com.ultras.bans.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fire-and-forget Discord webhook sender. Requests are queued on a single background thread and throttled to
 * the configured rate so bursts of punishments never trigger Discord's rate limiter or block the server.
 * Uses only java.net.http (JDK built-in) and the already-shaded Gson - no extra dependency to fetch.
 */
public final class DiscordWebhookClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Logger logger;
    private final ExecutorService worker;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private volatile long minIntervalMillis = 250;
    private volatile boolean running = true;

    public DiscordWebhookClient(Logger logger) {
        this.logger = logger;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ULTRASbans-Discord");
            t.setDaemon(true);
            return t;
        });
        worker.submit(this::pump);
    }

    public void setRateLimitPerSecond(int perSecond) {
        this.minIntervalMillis = Math.max(50, 1000L / Math.max(1, perSecond));
    }

    private void pump() {
        long last = 0;
        while (running) {
            try {
                Runnable task = queue.take();
                long wait = minIntervalMillis - (System.currentTimeMillis() - last);
                if (wait > 0) Thread.sleep(wait);
                task.run();
                last = System.currentTimeMillis();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Discord webhook task failed", ex);
            }
        }
    }

    public void sendEmbed(String webhookUrl, String colorHex, String title, String description, String footer) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        queue.add(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", trim(title, 256));
                embed.addProperty("description", trim(description, 4096));
                embed.addProperty("color", parseColor(colorHex));
                if (footer != null && !footer.isBlank()) {
                    JsonObject f = new JsonObject();
                    f.addProperty("text", trim(footer, 2048));
                    embed.add("footer", f);
                }
                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                JsonObject body = new JsonObject();
                body.add("embeds", embeds);
                post(webhookUrl, body.toString());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to build/send Discord embed", ex);
            }
        });
    }

    private void post(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 429) {
            // Rate-limited: requeue once after Discord's suggested retry window (default 1s if header missing).
            long retryMs = 1000;
            try {
                JsonObject j = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                if (j.has("retry_after")) retryMs = (long) (j.get("retry_after").getAsDouble() * 1000);
            } catch (Exception ignored) { }
            long delay = retryMs;
            queue.add(() -> { try { Thread.sleep(delay); post(url, json); } catch (Exception ignored) { } });
        } else if (resp.statusCode() >= 300) {
            logger.warning("Discord webhook returned HTTP " + resp.statusCode() + ": " + trim(resp.body(), 200));
        }
    }

    private static int parseColor(String hex) {
        try { return Integer.parseInt(hex.replace("#", ""), 16); } catch (Exception e) { return 0x999999; }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public void shutdown() {
        running = false;
        worker.shutdownNow();
    }
}
