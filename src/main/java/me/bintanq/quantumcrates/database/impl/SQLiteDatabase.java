package me.bintanq.quantumcrates.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.database.AbstractDatabase;
import me.bintanq.quantumcrates.util.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQLiteDatabase — HikariCP-backed SQLite implementation.
 *
 * SQLite is single-writer but HikariCP pool-size=1 ensures we never
 * get "database is locked" while still benefiting from connection reuse.
 */
public class SQLiteDatabase extends AbstractDatabase {

    private HikariDataSource dataSource;

    public SQLiteDatabase(QuantumCrates plugin) {
        super(plugin);
    }

    @Override
    public void init() throws Exception {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.sqlite.file", "quantumcrates.db"));
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        HikariConfig config = new HikariConfig();
        config.setPoolName("QuantumCrates-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // SQLite is single-writer — pool size 1 avoids "database is locked"
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        // SQLite-specific pragmas for performance & safety
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous",  "NORMAL");
        config.addDataSourceProperty("cache_size",   "10000");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("foreign_keys", "ON");

        dataSource = new HikariDataSource(config);
        Logger.info("SQLite pool initialised — file: &e" + dbFile.getName());

        createTables();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Logger.info("SQLite connection pool closed.");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /* ─────────────────────── Dialect overrides ─────────────────────── */

    @Override
    protected String autoIncrementSyntax() {
        // SQLite uses AUTOINCREMENT keyword
        return " AUTOINCREMENT";
    }

    @Override
    protected String upsertPlayerDataSql() {
        // SQLite UPSERT syntax (available since 3.24.0)
        return """
            INSERT INTO qc_player_data (uuid, pity_data, cooldown_data, last_seen)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                pity_data     = excluded.pity_data,
                cooldown_data = excluded.cooldown_data,
                last_seen     = excluded.last_seen
        """;
    }

    @Override
    protected String upsertAddKeysSql() {
        return """
            INSERT INTO qc_virtual_keys (uuid, key_id, amount)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, key_id) DO UPDATE SET
                amount = qc_virtual_keys.amount + excluded.amount
        """;
    }
}
