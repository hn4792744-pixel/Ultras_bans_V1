package com.ultras.bans.proxy.common;

import com.ultras.bans.database.DatabaseConfig;
import com.ultras.bans.database.DatabaseType;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Bukkit-free twin of config/DatabaseConfigLoader, for the Velocity and BungeeCord proxy modules which cannot
 * depend on org.bukkit.*. Reads the exact same database.yml format using the plugin's own shaded SnakeYAML.
 */
public final class ProxyDatabaseConfigLoader {

    private ProxyDatabaseConfigLoader() {}

    @SuppressWarnings("unchecked")
    public static DatabaseConfig load(File file, Logger logger) {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(file)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        } catch (Exception ex) {
            logger.warning("Could not read database.yml (" + ex.getMessage() + "), using SQLite defaults.");
            root = Map.of();
        }

        DatabaseType type;
        try {
            type = DatabaseType.valueOf(String.valueOf(root.getOrDefault("type", "SQLITE")).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid database type, falling back to SQLITE (note: SQLite cannot be shared across a proxy network).");
            type = DatabaseType.SQLITE;
        }

        Map<String, Object> sqlite = sub(root, "sqlite");
        Map<String, Object> mysql = sub(root, "mysql");

        return DatabaseConfig.builder()
                .type(type)
                .sqliteFileName(str(sqlite, "file", "ultrasbans.db"))
                .host(str(mysql, "host", "localhost"))
                .port(intVal(mysql, "port", 3306))
                .database(str(mysql, "database", "ultrasbans"))
                .username(str(mysql, "username", "root"))
                .password(str(mysql, "password", ""))
                .useSsl(boolVal(mysql, "use-ssl", false))
                .poolSize(intVal(mysql, "pool-size", 8))
                .tablePrefix(str(root, "table-prefix", "ultras_"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private static boolean boolVal(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }
}
