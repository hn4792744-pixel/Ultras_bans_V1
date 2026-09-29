package com.ultras.bans.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.ultras.bans.database.DatabaseConfig;
import com.ultras.bans.database.DatabaseExecutor;
import com.ultras.bans.database.DatabaseManager;
import com.ultras.bans.database.PunishmentRepositoryJdbc;
import com.ultras.bans.proxy.common.ProxyBanMessages;
import com.ultras.bans.proxy.common.ProxyDatabaseConfigLoader;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

/**
 * Velocity-side half of ULTRAS_bans_v1 (spec section 54). Rejects banned players (by UUID) and banned IPs at
 * the proxy edge - before they ever occupy a backend server slot - by reading the SAME MySQL/MariaDB database
 * every backend Paper server uses. This is what makes a ban issued on one backend server "network-wide": the
 * database is the single source of truth, and both this proxy plugin and every backend server's login check
 * query it.
 * <p>
 * Requires the shared database to be MySQL/MariaDB (see database.yml); SQLite is a single local file and
 * cannot be safely shared between the proxy process and backend server processes.
 */
@Plugin(id = "ultrasbans", name = "ULTRAS_bans_v1", version = "1.0.0", authors = "ULTRAS")
public final class UltrasBansVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private DatabaseManager databaseManager;
    private DatabaseExecutor executor;
    private PunishmentRepositoryJdbc repository;
    private ProxyBanMessages banMessages;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Inject
    public UltrasBansVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            File dir = dataDirectory.toFile();
            if (!dir.exists()) dir.mkdirs();
            File dbFile = new File(dir, "database.yml");
            if (!dbFile.exists()) {
                logger.warn("database.yml not found in {} - copy the one from a backend server (MySQL mode) here.", dir);
            }
            java.util.logging.Logger jul = java.util.logging.Logger.getLogger("ULTRASbans-Velocity");
            DatabaseConfig config = ProxyDatabaseConfigLoader.load(dbFile, jul);
            databaseManager = new DatabaseManager(config, dir, jul);
            databaseManager.connect();
            executor = new DatabaseExecutor(jul);
            repository = new PunishmentRepositoryJdbc(databaseManager, executor, jul);
            banMessages = ProxyBanMessages.load(dir, "en", "https://discord.gg/ucx", jul);
            logger.info("ULTRAS_bans_v1 (Velocity) connected to the shared database ({}).", config.type());
        } catch (Exception ex) {
            logger.error("Failed to initialize ULTRAS_bans_v1 on Velocity; ban enforcement at the proxy is DISABLED.", ex);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (executor != null) executor.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (repository == null) return;
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        try {
            List<PunishmentRecord> active = repository.findActiveByIpAndType(ip, PunishmentType.IP_BAN).get();
            for (PunishmentRecord r : active) {
                if (r.status().isActive() && !r.isExpiredByTime(System.currentTimeMillis())) {
                    Component msg = LEGACY.deserialize(banMessages.ipBanScreen(r));
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(msg));
                    return;
                }
            }
        } catch (Exception ex) {
            logger.error("IP ban lookup failed for {}", ip, ex);
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (repository == null) return;
        var uuid = event.getPlayer().getUniqueId();
        try {
            List<PunishmentRecord> active = repository.findActiveByPlayerAndType(uuid, PunishmentType.BAN).get();
            for (PunishmentRecord r : active) {
                if (r.status().isActive() && !r.isExpiredByTime(System.currentTimeMillis())) {
                    Component msg = LEGACY.deserialize(banMessages.banScreen(r));
                    event.setResult(com.velocitypowered.api.event.connection.LoginEvent.ComponentResult.denied(msg));
                    return;
                }
            }
        } catch (Exception ex) {
            logger.error("Ban lookup failed for {}", uuid, ex);
        }
    }
}
