package com.ultras.bans.config;

import com.ultras.bans.database.DatabaseConfig;
import com.ultras.bans.database.DatabaseType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class DatabaseConfigLoader {

    private DatabaseConfigLoader() {}

    public static DatabaseConfig load(File file, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        DatabaseType type;
        try {
            type = DatabaseType.valueOf(yaml.getString("type", "SQLITE").trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid database type in database.yml, falling back to SQLITE.");
            type = DatabaseType.SQLITE;
        }

        return DatabaseConfig.builder()
                .type(type)
                .sqliteFileName(yaml.getString("sqlite.file", "ultrasbans.db"))
                .host(yaml.getString("mysql.host", "localhost"))
                .port(yaml.getInt("mysql.port", 3306))
                .database(yaml.getString("mysql.database", "ultrasbans"))
                .username(yaml.getString("mysql.username", "root"))
                .password(yaml.getString("mysql.password", ""))
                .useSsl(yaml.getBoolean("mysql.use-ssl", false))
                .poolSize(yaml.getInt("mysql.pool-size", 8))
                .tablePrefix(yaml.getString("table-prefix", "ultras_"))
                .build();
    }
}
