package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Resolves command arguments to a target: online player, offline player known to the
 * database, raw UUID, or raw IPv4/IPv6 address. Never calls Bukkit.getOfflinePlayer(name)
 * (it can block on Mojang lookups) - offline lookups go through the async player repository.
 */
public final class TargetResolver {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$");

    private final UltrasBansPlugin plugin;

    public TargetResolver(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean looksLikeIp(String s) {
        return IPV4.matcher(s).matches() || (s.contains(":") && s.matches("^[0-9a-fA-F:]+$"));
    }

    /** Must be called from the main thread (touches Bukkit online-player list); result completes async. */
    public CompletableFuture<ResolvedTarget> resolve(String input, boolean allowRawIp) {
        if (allowRawIp && looksLikeIp(input)) {
            return CompletableFuture.completedFuture(new ResolvedTarget(null, null, input));
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            String ip = online.getAddress() != null && online.getAddress().getAddress() != null
                    ? online.getAddress().getAddress().getHostAddress() : null;
            return CompletableFuture.completedFuture(new ResolvedTarget(online.getUniqueId(), online.getName(), ip));
        }

        if (UUID_PATTERN.matcher(input).matches()) {
            UUID uuid = UUID.fromString(input);
            return plugin.playerRepository().findByUuid(uuid).thenApply(p ->
                    p == null ? new ResolvedTarget(uuid, input, null) : fromProfile(p));
        }

        return plugin.playerRepository().findByName(input).thenApply(p -> p == null ? null : fromProfile(p));
    }

    private ResolvedTarget fromProfile(PlayerProfile p) {
        return new ResolvedTarget(p.uuid(), p.username(), p.lastIp());
    }
}
