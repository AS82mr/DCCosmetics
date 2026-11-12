package dc.dcessentials.core.database;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneSnapshot;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class DatabaseManager {

    private final Logger log = DCCosmetics.getInstance().getLogger();
    private Connection connection;
    private boolean useMySQL = false;
    private String host, port, database, username, password;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DCEssentials-DB-Worker");
        t.setDaemon(true);
        return t;
    });

    // Fast in-memory cache for DCNPC questline to eliminate Skript database bottlenecks
    private final Map<UUID, Integer> questIndexCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> metNpcsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> claimedStagesCache = new ConcurrentHashMap<>();

    public void executeAsync(Runnable task) {
        if (dbExecutor.isShutdown() || !DCCosmetics.getInstance().isEnabled()) {
            task.run();
        } else {
            dbExecutor.submit(task);
        }
    }

    public void init() {
        var mainCfg = DCCosmetics.getInstance().getConfig();
        if (mainCfg.isConfigurationSection("database") || mainCfg.isConfigurationSection("mysql")) {
            boolean isMysql = "mysql".equalsIgnoreCase(mainCfg.getString("database.type", "sqlite"))
                    || mainCfg.getBoolean("database.mysql.enabled", false)
                    || mainCfg.getBoolean("mysql.enabled", false);
            this.useMySQL = isMysql;
            String prefix = mainCfg.isConfigurationSection("database.mysql") ? "database.mysql." : (mainCfg.isConfigurationSection("database") ? "database." : "mysql.");
            this.host = mainCfg.getString(prefix + "host", "localhost");
            this.port = mainCfg.getString(prefix + "port", "3306");
            this.database = mainCfg.getString(prefix + "database", "dragoncote");
            this.username = mainCfg.getString(prefix + "username", "root");
            this.password = mainCfg.getString(prefix + "password", "");
        } else {
            File dbConfigFile = new File(DCCosmetics.getInstance().getDataFolder(), "database.yml");
            if (dbConfigFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(dbConfigFile);
                this.useMySQL = config.getBoolean("mysql.enabled", false);
                this.host = config.getString("mysql.host", "localhost");
                this.port = config.getString("mysql.port", "3306");
                this.database = config.getString("mysql.database", "dragoncote");
                this.username = config.getString("mysql.username", "root");
                this.password = config.getString("mysql.password", "");
            } else {
                this.useMySQL = false;
            }
        }

        connect();
        createTables();
    }

    private void connect() {
        try {
            if (useMySQL) {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, username, password);
                log.info("[Database] Connected successfully to MySQL database: " + database);
            } else {
                File dataDir = new File(DCCosmetics.getInstance().getDataFolder(), "data");
                if (!dataDir.exists()) dataDir.mkdirs();
                File dbFile = new File(dataDir, "database.db");

                // Seamless migration from legacy root data.db if present
                File legacyDb = new File(DCCosmetics.getInstance().getDataFolder(), "data.db");
                if (legacyDb.exists() && !dbFile.exists()) {
                    legacyDb.renameTo(dbFile);
                }
                if (!dbFile.exists() && legacyDb.exists()) {
                    dbFile = legacyDb;
                }

                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                try (Statement pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA journal_mode=WAL;");
                    pragma.execute("PRAGMA synchronous=NORMAL;");
                    pragma.execute("PRAGMA busy_timeout=5000;");
                } catch (SQLException ignored) {}
                log.info("[Database] Connected successfully to local SQLite storage (" + dbFile.getName() + ", WAL mode).");
            }
        } catch (SQLException e) {
            log.severe("[Database] Connection failed: " + e.getMessage());
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    private void createTables() {
        try (Statement s = getConnection().createStatement()) {
            // 1. Keystones
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_keystones (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "tier VARCHAR(16), " +
                    "energy INT, " +
                    "heat INT, " +
                    "shield_until BIGINT)");

            // 2. RNG Player selections & XP
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_rng_player (" +
                    "uuid VARCHAR(36), " +
                    "category VARCHAR(32), " +
                    "selected_drop VARCHAR(64), " +
                    "rng_xp INT, " +
                    "PRIMARY KEY(uuid, category))");

            // 3. Pity Player tiers & XP
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_pity_player (" +
                    "uuid VARCHAR(36), " +
                    "category VARCHAR(32), " +
                    "current_tier INT, " +
                    "pity_xp INT, " +
                    "PRIMARY KEY(uuid, category))");

            // 4. DCNPC Questline Persistence
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_quest_progress (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "quest_index INT, " +
                    "active_node VARCHAR(64), " +
                    "met_npcs TEXT, " +
                    "claimed_stages TEXT)");

            // 5. Item Tracking Ledger (replaces monolithic item_tracker_ledger.yml)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_item_tracking (" +
                    "instance_uuid VARCHAR(36) PRIMARY KEY, " +
                    "item_id VARCHAR(64) NOT NULL, " +
                    "user_uuid VARCHAR(36), " +
                    "user_name VARCHAR(64), " +
                    "location_type VARCHAR(32) NOT NULL, " +
                    "world VARCHAR(64), " +
                    "x INT, " +
                    "y INT, " +
                    "z INT, " +
                    "vault_number INT, " +
                    "amount INT, " +
                    "last_seen BIGINT)");
            try {
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dce_tracking_item ON dce_item_tracking(item_id)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dce_tracking_user ON dce_item_tracking(user_uuid)");
            } catch (SQLException ignored) {}

            // 6. DCEPet Player Persistence Table
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_pets_player (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "active_pet_id VARCHAR(64), " +
                    "visibility_mode VARCHAR(16) NOT NULL DEFAULT 'ALL', " +
                    "auto_cast INT NOT NULL DEFAULT 1, " +
                    "pets_data TEXT, " +
                    "allies_data TEXT)");

            // 7. Keystone v2 Schema Migration & Additional Tables (Section 5.1)
            if (!useMySQL && !hasColumn("dce_keystones", "micro")) {
                backupSqliteBeforeV2Migration(s);
            }
            ensureColumn("dce_keystones", "micro", "ALTER TABLE dce_keystones ADD COLUMN micro INT NOT NULL DEFAULT 0");
            ensureColumn("dce_keystones", "overloaded", "ALTER TABLE dce_keystones ADD COLUMN overloaded INT NOT NULL DEFAULT 0");
            ensureColumn("dce_keystones", "updated_at", "ALTER TABLE dce_keystones ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_meta (" +
                    "k VARCHAR(32) PRIMARY KEY, " +
                    "v VARCHAR(64))");
            s.executeUpdate("REPLACE INTO dce_meta (k, v) VALUES ('schema_version', '2')");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_player_settings (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "tooltip_mode VARCHAR(10) NOT NULL DEFAULT 'COMPACT')");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS dce_cube_drops (" +
                    "uuid VARCHAR(36), " +
                    "hour_bucket BIGINT, " +
                    "n INT, " +
                    "PRIMARY KEY(uuid, hour_bucket))");

            log.info("[Database] All core tables (schema v2) verified and ready.");
        } catch (SQLException e) {
            log.warning("[Database] Error creating database tables: " + e.getMessage());
        }
    }

    private void backupSqliteBeforeV2Migration(Statement s) {
        try {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE);");
        } catch (SQLException ignored) {}
        try {
            File dataDir = new File(DCCosmetics.getInstance().getDataFolder(), "data");
            File dbFile = new File(dataDir, "database.db");
            if (!dbFile.exists()) return;
            File backupDir = new File(dataDir, "backup");
            if (!backupDir.exists()) backupDir.mkdirs();
            File dest = new File(backupDir, "database-pre-v2-" + System.currentTimeMillis() + ".db");
            Files.copy(dbFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("[Database] Created pre-v2 SQLite backup at " + dest.getAbsolutePath());
        } catch (Exception e) {
            log.warning("[Database] Failed to create pre-v2 SQLite backup: " + e.getMessage());
        }
    }

    public synchronized boolean hasColumn(String table, String column) {
        try {
            DatabaseMetaData meta = getConnection().getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    if (colName != null && colName.equalsIgnoreCase(column)) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            log.warning("[Database] Error checking column " + table + "." + column + ": " + e.getMessage());
        }
        return false;
    }

    public synchronized void ensureColumn(String table, String column, String ddl) {
        if (hasColumn(table, column)) return;
        try (Statement s = getConnection().createStatement()) {
            s.executeUpdate(ddl);
            log.info("[Database] Applied schema migration: " + ddl);
        } catch (SQLException e) {
            log.warning("[Database] Error applying migration (" + ddl + "): " + e.getMessage());
        }
    }

    // =========================================================================
    // KEYSTONE ASYNC PERSISTENCE (v2 Snapshot Architecture)
    // =========================================================================

    public void loadKeystoneSnapshot(UUID uuid, Consumer<Optional<KeystoneSnapshot>> callbackMainThread) {
        executeAsync(() -> {
            Optional<KeystoneSnapshot> result = Optional.empty();
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT tier, energy, micro, heat, overloaded, shield_until, updated_at FROM dce_keystones WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            KeystoneSnapshot snap = new KeystoneSnapshot(
                                    uuid,
                                    KeystoneTier.fromString(rs.getString("tier")),
                                    rs.getInt("energy"),
                                    rs.getInt("micro"),
                                    rs.getInt("heat"),
                                    rs.getInt("overloaded") != 0,
                                    rs.getLong("shield_until"),
                                    rs.getLong("updated_at")
                            );
                            result = Optional.of(snap);
                        }
                    }
                } catch (SQLException e) {
                    log.warning("[Database] Error loading Keystone snapshot: " + e.getMessage());
                }
            }
            if (callbackMainThread != null) {
                final Optional<KeystoneSnapshot> finalResult = result;
                if (DCCosmetics.getInstance().isEnabled() && !Bukkit.isPrimaryThread()) {
                    Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> callbackMainThread.accept(finalResult));
                } else {
                    callbackMainThread.accept(finalResult);
                }
            }
        });
    }

    public void saveKeystoneSnapshot(KeystoneSnapshot snap) {
        if (snap == null) return;
        executeAsync(() -> writeKeystoneSnapshotSync(snap));
    }

    public void saveKeystoneSnapshotsBatch(Collection<KeystoneSnapshot> snapshots, boolean sync) {
        if (snapshots == null || snapshots.isEmpty()) return;
        List<KeystoneSnapshot> copy = new ArrayList<>(snapshots);
        Runnable task = () -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "REPLACE INTO dce_keystones (uuid, tier, energy, micro, heat, overloaded, shield_until, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (KeystoneSnapshot snap : copy) {
                        bindSnapshot(ps, snap);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                } catch (SQLException e) {
                    log.warning("[Database] Error saving Keystone snapshot batch: " + e.getMessage());
                }
            }
        };
        if (sync || dbExecutor.isShutdown() || !DCCosmetics.getInstance().isEnabled()) {
            task.run();
        } else {
            dbExecutor.submit(task);
        }
    }

    private synchronized void writeKeystoneSnapshotSync(KeystoneSnapshot snap) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "REPLACE INTO dce_keystones (uuid, tier, energy, micro, heat, overloaded, shield_until, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindSnapshot(ps, snap);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[Database] Error saving Keystone snapshot: " + e.getMessage());
        }
    }

    private void bindSnapshot(PreparedStatement ps, KeystoneSnapshot snap) throws SQLException {
        ps.setString(1, snap.uuid().toString());
        ps.setString(2, snap.tier() != null ? snap.tier().name() : KeystoneTier.NONE.name());
        ps.setInt(3, snap.energy());
        ps.setInt(4, snap.micro());
        ps.setInt(5, snap.heat());
        ps.setInt(6, snap.overloaded() ? 1 : 0);
        ps.setLong(7, snap.shieldUntil());
        ps.setLong(8, snap.updatedAt());
    }

    public void loadKeystoneProfile(KeystoneProfile profile) {
        loadKeystoneProfile(profile, null);
    }

    public void loadKeystoneProfile(KeystoneProfile profile, Runnable onCompleteMainThread) {
        loadKeystoneSnapshot(profile.getUuid(), opt -> {
            opt.ifPresent(profile::applySnapshot);
            profile.setLoaded(true);
            if (onCompleteMainThread != null) {
                onCompleteMainThread.run();
            }
        });
    }

    public void saveKeystoneProfile(KeystoneProfile profile) {
        if (profile == null) return;
        KeystoneSnapshot snap = profile.toSnapshot();
        profile.setDirty(false);
        saveKeystoneSnapshot(snap);
    }

    public void loadPlayerTooltipMode(UUID uuid, Consumer<String> callback) {
        executeAsync(() -> {
            String mode = "COMPACT";
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT tooltip_mode FROM dce_player_settings WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String val = rs.getString("tooltip_mode");
                            if (val != null && !val.isBlank()) {
                                mode = val.toUpperCase(Locale.ROOT);
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.warning("[Database] Error loading player tooltip_mode: " + e.getMessage());
                }
            }
            if (callback != null) {
                callback.accept(mode);
            }
        });
    }

    public void savePlayerTooltipMode(UUID uuid, String mode) {
        if (uuid == null || mode == null) return;
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "REPLACE INTO dce_player_settings (uuid, tooltip_mode) VALUES (?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, mode.toUpperCase(Locale.ROOT));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.warning("[Database] Error saving player tooltip_mode: " + e.getMessage());
                }
            }
        });
    }

    public void loadRngPlayer(UUID uuid, dc.dcessentials.modules.rng.RngManager rng) {
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT category, selected_drop, rng_xp FROM dce_rng_player WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cat = rs.getString("category");
                            String sel = rs.getString("selected_drop");
                            int xp = rs.getInt("rng_xp");
                            rng.setInitialState(uuid, cat, sel, xp);
                        }
                    }
                } catch (SQLException e) {
                    log.warning("[Database] Error loading RNG state: " + e.getMessage());
                }
            }
        });
    }

    public void saveRngPlayer(UUID uuid, String category, String selectedDrop, int rngXp) {
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "REPLACE INTO dce_rng_player (uuid, category, selected_drop, rng_xp) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, category);
                    ps.setString(3, selectedDrop != null ? selectedDrop : "none");
                    ps.setInt(4, rngXp);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.warning("[Database] Error saving RNG state: " + e.getMessage());
                }
            }
        });
    }

    public void loadPityPlayer(UUID uuid, dc.dcessentials.modules.pity.PityManager pity) {
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT category, pity_xp FROM dce_pity_player WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cat = rs.getString("category");
                            int counter = rs.getInt("pity_xp");
                            pity.setInitialCounter(uuid, cat, counter);
                        }
                    }
                } catch (SQLException e) {
                    log.warning("[Database] Error loading pity state: " + e.getMessage());
                }
            }
        });
    }

    public void savePityPlayer(UUID uuid, String category, int counter) {
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "REPLACE INTO dce_pity_player (uuid, category, current_tier, pity_xp) VALUES (?, ?, 1, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, category);
                    ps.setInt(3, counter);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.warning("[Database] Error saving pity state: " + e.getMessage());
                }
            }
        });
    }

    // =========================================================================
    // DCNPC QUESTLINE PERSISTENCE BRIDGE (Zero-Lag SQL Caching)
    // =========================================================================

    public int getQuestIndex(UUID uuid) {
        return questIndexCache.getOrDefault(uuid, 1);
    }

    public void setQuestIndex(UUID uuid, int idx, String activeNode) {
        questIndexCache.put(uuid, idx);
        executeAsync(() -> {
            synchronized (DatabaseManager.this) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "INSERT INTO dce_quest_progress (uuid, quest_index, active_node) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE quest_index = VALUES(quest_index), active_node = VALUES(active_node)")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, idx);
                    ps.setString(3, activeNode);
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // SQLite fallback
                    try (PreparedStatement ps2 = getConnection().prepareStatement(
                            "REPLACE INTO dce_quest_progress (uuid, quest_index, active_node) VALUES (?, ?, ?)")) {
                        ps2.setString(1, uuid.toString());
                        ps2.setInt(2, idx);
                        ps2.setString(3, activeNode);
                        ps2.executeUpdate();
                    } catch (SQLException e) {
                        log.warning("[Database] Error saving quest progress: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void close() {
        try {
            dbExecutor.shutdown();
            dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            synchronized (this) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (SQLException ignored) {}
    }
}
