package com.ultras.bans.proxy.bungee;

import com.ultras.bans.database.DatabaseConfig;
import com.ultras.bans.database.DatabaseExecutor;
import com.ultras.bans.database.DatabaseManager;
import com.ultras.bans.database.PunishmentRepositoryJdbc;
import com.ultras.bans.proxy.common.ProxyBanMessages;
import com.ultras.bans.proxy.common.ProxyDatabaseConfigLoader;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BungeeCord/Waterfall-side half of ULTRAS_bans_v1 (spec section 54), functionally identical to the Velocity
 * module: rejects IP-banned and UUID-banned connections at the proxy edge by reading the same shared MySQL/MariaDB
 * database backend servers use, using Bungee's async login intents so the lookup never blocks the proxy thread.
 */
public final class UltrasBansBungeePlugin extends Plugin implements Listener {

    private DatabaseManager databaseManager;
    private DatabaseExecutor executor;
    private PunishmentRepositoryJdbc repository;
    private ProxyBanMessages banMessages;

    @Override
    public void onEnable() {
        try {
            File dir = getDataFolder();
            if (!dir.exists()) dir.mkdirs();
            File dbFile = new File(dir, "database.yml");
            if (!dbFile.exists()) {
                getLogger().warning("database.yml not found in " + dir + " - copy the one from a backend server (MySQL mode) here.");
            }
            Logger jul = Logger.getLogger("ULTRASbans-Bungee");
            DatabaseConfig config = ProxyDatabaseConfigLoader.load(dbFile, jul);
            databaseManager = new DatabaseManager(config, dir, jul);
            databaseManager.connect();
            executor = new DatabaseExecutor(jul);
            repository = new PunishmentRepositoryJdbc(databaseManager, executor, jul);
            banMessages = ProxyBanMessages.load(dir, "en", "https://discord.gg/ucx", jul);
            getProxy().getPluginManager().registerListener(this, this);
            getLogger().info("ULTRAS_bans_v1 (BungeeCord) connected to the shared database (" + config.type() + ").");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize ULTRAS_bans_v1 on BungeeCord; ban enforcement at the proxy is DISABLED.", ex);
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) executor.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        if (repository == null) return;
        event.registerIntent(this);
        String ip = event.getConnection().getAddress().getAddress().getHostAddress();
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                List<PunishmentRecord> active = repository.findActiveByIpAndType(ip, PunishmentType.IP_BAN).get();
                for (PunishmentRecord r : active) {
                    if (r.status().isActive() && !r.isExpiredByTime(System.currentTimeMillis())) {
                        event.setCancelled(true);
                        event.setCancelReason(TextComponent.fromLegacyText(
                                ChatColor.translateAlternateColorCodes('&', banMessages.ipBanScreen(r))));
                        break;
                    }
                }
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "IP ban lookup failed for " + ip, ex);
            } finally {
                event.completeIntent(this);
            }
        });
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        if (repository == null) return;
        event.registerIntent(this);
        PendingConnection conn = event.getConnection();
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                if (conn.getUniqueId() == null) return;
                List<PunishmentRecord> active = repository.findActiveByPlayerAndType(conn.getUniqueId(), PunishmentType.BAN).get();
                for (PunishmentRecord r : active) {
                    if (r.status().isActive() && !r.isExpiredByTime(System.currentTimeMillis())) {
                        event.setCancelled(true);
                        event.setCancelReason(TextComponent.fromLegacyText(
                                ChatColor.translateAlternateColorCodes('&', banMessages.banScreen(r))));
                        break;
                    }
                }
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Ban lookup failed for " + conn.getUniqueId(), ex);
            } finally {
                event.completeIntent(this);
            }
        });
    }
}
