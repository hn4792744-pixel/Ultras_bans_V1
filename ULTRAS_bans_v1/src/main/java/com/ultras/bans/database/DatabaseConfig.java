package com.ultras.bans.database;

public final class DatabaseConfig {

    private final DatabaseType type;
    private final String sqliteFileName;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSsl;
    private final int poolSize;
    private final String tablePrefix;

    private DatabaseConfig(Builder b) {
        this.type = b.type;
        this.sqliteFileName = b.sqliteFileName;
        this.host = b.host;
        this.port = b.port;
        this.database = b.database;
        this.username = b.username;
        this.password = b.password;
        this.useSsl = b.useSsl;
        this.poolSize = b.poolSize;
        this.tablePrefix = b.tablePrefix;
    }

    public DatabaseType type() { return type; }
    public String sqliteFileName() { return sqliteFileName; }
    public String host() { return host; }
    public int port() { return port; }
    public String database() { return database; }
    public String username() { return username; }
    public String password() { return password; }
    public boolean useSsl() { return useSsl; }
    public int poolSize() { return poolSize; }
    public String tablePrefix() { return tablePrefix; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DatabaseType type = DatabaseType.SQLITE;
        private String sqliteFileName = "ultrasbans.db";
        private String host = "localhost";
        private int port = 3306;
        private String database = "ultrasbans";
        private String username = "root";
        private String password = "";
        private boolean useSsl = false;
        private int poolSize = 8;
        private String tablePrefix = "ultras_";

        public Builder type(DatabaseType t) { this.type = t; return this; }
        public Builder sqliteFileName(String s) { this.sqliteFileName = s; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder port(int p) { this.port = p; return this; }
        public Builder database(String d) { this.database = d; return this; }
        public Builder username(String u) { this.username = u; return this; }
        public Builder password(String p) { this.password = p; return this; }
        public Builder useSsl(boolean s) { this.useSsl = s; return this; }
        public Builder poolSize(int p) { this.poolSize = p; return this; }
        public Builder tablePrefix(String p) { this.tablePrefix = p; return this; }

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}
