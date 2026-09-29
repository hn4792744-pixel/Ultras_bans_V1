package com.ultras.bans.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private static final DateTimeFormatter ABSOLUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtil() {}

    /** Formats an epoch-millis instant as an absolute local date/time, e.g. "2026-09-27 14:05:00". */
    public static String formatAbsolute(long epochMillis) {
        if (epochMillis <= 0) return "-";
        return ABSOLUTE.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Formats a duration in millis as a compact "1d 2h 3m" style string. Zero or negative returns "0m". */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0m";
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0 && days == 0 && hours == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public static String formatPermanentOrDuration(boolean permanent, long remainingMillis) {
        return permanent ? "Permanent" : formatDuration(remainingMillis);
    }
}
