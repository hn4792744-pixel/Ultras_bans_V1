package com.ultras.bans;

import com.ultras.bans.config.*;
import com.ultras.bans.database.*;
import com.ultras.bans.gui.GuiService;
import com.ultras.bans.manager.PlayerNameCache;
import com.ultras.bans.manager.PunishmentCache;
import com.ultras.bans.command.TargetResolver;
import com.ultras.bans.punishment.PunishmentMessageFactory;
import com.ultras.bans.punishment.PunishmentService;
import com.ultras.bans.punishment.PunishmentServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * Plugin entry point. Responsible ONLY for bootstrapping and wiring services -
 * business logic lives in the manager/service classes themselves (spec
 * section 58). Fields are populated incrementally as each subsystem
 * (commands, GUI, listeners, security, discord, proxy) comes online; see the
 * corresponding register*() method for each.
 */
public final class UltrasBansPlugin extends JavaPlugin {

    private static UltrasBansPlugin instance;

    private MainConfig mainConfig;
    private CommandsConfig commandsConfig;
    private LanguageManager languageManager;
    private PunishmentMessageFactory messageFactory;

    private DatabaseManager databaseManager;
    private DatabaseExecutor databaseExecutor;
    private PunishmentRepositoryJdbc punishmentRepository;
    private PlayerRepositoryJdbc playerRepository;

    private PunishmentCache punishmentCache;
    private PunishmentServiceImpl punishmentService;
    private final PlayerNameCache playerNameCache = new PlayerNameCache();
    private TargetResolver targetResolver;
    private GuiService guiService;
    private com.ultras.bans.manager.JailService jailService;
    private com.ultras.bans.manager.PermissionService permissionService;
    private com.ultras.bans.discord.DiscordService discordService;
    private com.ultras.bans.manager.SecurityService securityService;
    private com.ultras.bans.manager.ProxySyncService proxySyncService;
    private com.ultras.bans.manager.FreezeService freezeService;
    private com.ultras.bans.manager.VanishService vanishService;
    private com.ultras.bans.manager.ExpirationScheduler expirationScheduler;
    private com.ultras.bans.listener.PlayerConnectionListener connectionListener;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        saveDefaultConfig(); // config.yml
        saveResourceIfMissing("database.yml");
        saveResourceIfMissing("commands.yml");
        saveResourceIfMissing("jail.yml");
        saveResourceIfMissing("permissions.yml");
        // gui/*.yml and lang files are extracted lazily by their own managers.

        try {
            loadConfigs();
            connectDatabase();
            initEngine();
            registerCommands();
            registerListeners();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to enable ULTRAS_bans_v1, disabling plugin.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("ULTRAS_bans_v1 enabled in " + (System.currentTimeMillis() - start) + "ms.");
    }

    @Override
    public void onDisable() {
        if (proxySyncService != null) {
            proxySyncService.stop();
        }
        if (connectionListener != null) {
            connectionListener.flushAll();
        }
        if (expirationScheduler != null) {
            expirationScheduler.shutdown();
        }
        if (discordService != null) {
            discordService.shutdown();
        }
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("ULTRAS_bans_v1 disabled.");
    }

    // -------------------------------------------------------------------
    // Bootstrapping steps
    // -------------------------------------------------------------------

    private void loadConfigs() {
        mainConfig = MainConfig.load(new File(getDataFolder(), "config.yml"));
        commandsConfig = CommandsConfig.load(new File(getDataFolder(), "commands.yml"));

        languageManager = new LanguageManager(getDataFolder(), getLogger());
        languageManager.ensureDefaults(LangFiles.ALL, getClassLoader());
        languageManager.load(mainConfig.language(), LangFiles.ALL);
        languageManager.validateParity(LangFiles.ALL);

        messageFactory = new PunishmentMessageFactory(languageManager, mainConfig);
    }

    private void connectDatabase() {
        DatabaseConfig dbConfig = DatabaseConfigLoader.load(new File(getDataFolder(), "database.yml"), getLogger());
        databaseManager = new DatabaseManager(dbConfig, getDataFolder(), getLogger());
        databaseManager.connect();

        databaseExecutor = new DatabaseExecutor(getLogger());
        punishmentRepository = new PunishmentRepositoryJdbc(databaseManager, databaseExecutor, getLogger());
        playerRepository = new PlayerRepositoryJdbc(databaseManager, databaseExecutor, getLogger());
    }

    private void initEngine() {
        targetResolver = new TargetResolver(this);
        punishmentCache = new PunishmentCache();
        punishmentService = new PunishmentServiceImpl(punishmentRepository, punishmentCache, databaseExecutor, getLogger());
        expirationScheduler = new com.ultras.bans.manager.ExpirationScheduler(punishmentService, getLogger());
        punishmentService.registerHook(expirationScheduler);
        // Warm the cache with active permanent + temporary punishments so hot-path checks
        // (login, chat, move) never need a synchronous DB call. See ExpirationScheduler (Phase 6)
        // for the recurring sweep that expires temporary punishments once their time is up.
        preloadActivePunishments();
        playerRepository.findAll(100000, 0).thenAccept(list -> list.forEach(p -> playerNameCache.add(p.username())));
    }

    private void preloadActivePunishments() {
        punishmentRepository.findAllActive().thenAccept(list -> {
            for (var record : list) {
                punishmentCache.putActive(record);
            }
            getLogger().info("Preloaded " + list.size() + " active punishment(s) into cache.");
            if (expirationScheduler != null) expirationScheduler.scheduleAll(list);
        }).exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "Failed to preload active punishments", ex);
            return null;
        });
    }

    private void registerCommands() {
        // Populated in command registration phase - see command/CommandRegistrar.
        com.ultras.bans.command.CommandRegistrar.registerAll(this);
    }

    private void registerListeners() {
        punishmentService.registerHook(new com.ultras.bans.punishment.PunishmentEnforcer(this));
        getServer().getPluginManager().registerEvents(new com.ultras.bans.listener.CommandConflictGuard(this), this);
        freezeService = new com.ultras.bans.manager.FreezeService(this);
        vanishService = new com.ultras.bans.manager.VanishService(this);
        jailService = new com.ultras.bans.manager.JailService(this,
                new com.ultras.bans.database.JailRepositoryJdbc(databaseManager, databaseExecutor, getLogger()));
        punishmentService.registerHook(jailService);
        getServer().getPluginManager().registerEvents(jailService, this);
        permissionService = new com.ultras.bans.manager.PermissionService(this,
                new com.ultras.bans.database.PermissionRepositoryJdbc(databaseManager, databaseExecutor, getLogger()));
        getServer().getPluginManager().registerEvents(permissionService, this);
        guiService = new com.ultras.bans.gui.GuiManager(this);
        discordService = new com.ultras.bans.discord.DiscordService(this);
        punishmentService.registerHook(discordService);
        securityService = new com.ultras.bans.manager.SecurityService(this,
                new com.ultras.bans.database.SecurityLogRepositoryJdbc(databaseManager, databaseExecutor, getLogger()));
        punishmentService.registerHook(securityService);
        proxySyncService = new com.ultras.bans.manager.ProxySyncService(this);
        if (mainConfig.proxySync()) {
            if (database().config().type() == com.ultras.bans.database.DatabaseType.MYSQL) {
                proxySyncService.start(100L); // every 5 seconds
            } else {
                getLogger().warning("features.proxy-sync is enabled but database type is SQLITE; proxy sync requires MySQL/MariaDB. Skipping.");
            }
        }
        getServer().getPluginManager().registerEvents(new com.ultras.bans.listener.DiscordEventListener(this), this);
        punishmentService.registerHook(freezeService);
        punishmentService.registerHook(vanishService);
        getServer().getPluginManager().registerEvents(freezeService, this);
        getServer().getPluginManager().registerEvents(vanishService, this);
        connectionListener = new com.ultras.bans.listener.PlayerConnectionListener(this);
        getServer().getPluginManager().registerEvents(connectionListener, this);
        // Join/quit tracking, ban/mute enforcement, freeze/jail/vanish listeners are added in the listener phase.
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    // -------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------

    public static UltrasBansPlugin get() {
        return instance;
    }

    public MainConfig mainConfig() { return mainConfig; }
    public CommandsConfig commandsConfig() { return commandsConfig; }
    public LanguageManager lang() { return languageManager; }
    public PunishmentMessageFactory messages() { return messageFactory; }
    public DatabaseManager database() { return databaseManager; }
    public PunishmentService punishmentService() { return punishmentService; }
    public PunishmentServiceImpl punishmentServiceImpl() { return punishmentService; }
    public PunishmentCache punishmentCache() { return punishmentCache; }
    public PlayerRepositoryJdbc playerRepository() { return playerRepository; }
    public DatabaseExecutor databaseExecutor() { return databaseExecutor; }
    public com.ultras.bans.listener.PlayerConnectionListener connections() { return connectionListener; }
    public PlayerNameCache playerNameCache() { return playerNameCache; }
    public TargetResolver targets() { return targetResolver; }
    public GuiService gui() { return guiService; }
    public com.ultras.bans.manager.JailService jail() { return jailService; }
    public com.ultras.bans.manager.PermissionService permissions() { return permissionService; }
    public com.ultras.bans.discord.DiscordService discord() { return discordService; }
    public com.ultras.bans.manager.SecurityService security() { return securityService; }
    public com.ultras.bans.database.PunishmentRepositoryJdbc punishmentRepositoryForSync() { return punishmentRepository; }
    public com.ultras.bans.manager.VanishService vanish() { return vanishService; }
    /** True when the inventory belongs to one of this plugin's GUIs (lets staff GUIs work even for frozen viewers). */
    public boolean isPluginGui(org.bukkit.inventory.Inventory inv) {
        return inv.getHolder() instanceof com.ultras.bans.gui.UltrasGuiHolder;
    }
    public void gui(GuiService service) { this.guiService = service; }

    /** Reloads config, lang, GUI and permission files without a server restart (spec section 49). Never reloads the DB connection. */
    public void reload() {
        loadConfigs();
        if (jailService != null) jailService.reloadConfig();
        if (permissionService != null) permissionService.reloadConfig();
        if (guiService != null) guiService.reload();
        if (discordService != null) discordService.reload();
        if (securityService != null) securityService.reloadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
