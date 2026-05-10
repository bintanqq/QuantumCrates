package me.bintanq.quantumcrates.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.database.AbstractDatabase;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQLDatabase — HikariCP-backed MySQL / MariaDB implementation.
 *
 * Connection pool tuned for high-concurrency Paper servers.
 * All queries are dialect-compatible with MariaDB as well.
 */
public class MySQLDatabase extends AbstractDatabase {

    private HikariDataSource dataSource;

    public MySQLDatabase(QuantumCrates plugin) {
        super(plugin);
    }

    @Override
    public void init() throws Exception {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("database.mysql");
        if (cfg == null) throw new IllegalStateException("MySQL config section missing in config.yml");

        String host     = cfg.getString("host",     "localhost");
        int    port     = cfg.getInt("port",         3306);
        String database = cfg.getString("database",  "quantumcrates");
        String user     = cfg.getString("username",  "root");
        String pass     = cfg.getString("password",  "");
        int    poolSize = cfg.getInt("pool-size",    10);

        HikariConfig config = new HikariConfig();
        config.setPoolName("QuantumCrates-MySQL");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&characterEncoding=utf8&serverTimezone=UTC",
                host, port, database));
        config.setUsername(user);
        config.setPassword(pass);

        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(cfg.getLong("connection-timeout", 30_000));
        config.setIdleTimeout(cfg.getLong("idle-timeout",             600_000));
        config.setMaxLifetime(cfg.getLong("max-lifetime",             1_800_000));

        // MySQL tuning properties
        config.addDataSourceProperty("cachePrepStmts",          "true");
        config.addDataSourceProperty("prepStmtCacheSize",        "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
        config.addDataSourceProperty("useServerPrepStmts",      "true");
        config.addDataSourceProperty("useLocalSessionState",    "true");
        config.addDataSourceProperty("rewriteBatchedStatements","true");   // critical for batch inserts
        config.addDataSourceProperty("cacheResultSetMetadata",  "true");
        config.addDataSourceProperty("cacheServerConfiguration","true");
        config.addDataSourceProperty("elideSetAutoCommits",     "true");
        config.addDataSourceProperty("maintainTimeStats",        "false");

        // Connection health check
        config.setConnectionTestQuery("SELECT 1");
        config.setKeepaliveTime(60_000);

        dataSource = new HikariDataSource(config);
        Logger.info("MySQL pool initialised — host: &e" + host + "&f, pool: &e" + poolSize);

        createTables();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Logger.info("MySQL connection pool closed.");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /* ─────────────────────── Dialect overrides ─────────────────────── */

    @Override
    protected String autoIncrementSyntax() {
        return "AUTO_INCREMENT";
    }

    @Override
    protected String upsertPlayerDataSql() {
        // MySQL INSERT ... ON DUPLICATE KEY UPDATE
        return """
            INSERT INTO qc_player_data (uuid, pity_data, cooldown_data, lifetime_opens, last_seen)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pity_data     = VALUES(pity_data),
                cooldown_data = VALUES(cooldown_data),
                lifetime_opens = VALUES(lifetime_opens),
                last_seen     = VALUES(last_seen)
        """;
    }

    @Override
    protected String upsertAddKeysSql() {
        return """
            INSERT INTO qc_virtual_keys (uuid, key_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)
        """;
    }
}
