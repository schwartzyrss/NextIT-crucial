package com.nextit.config;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages an embedded, persistent MariaDB instance for NextIT when no local or remote MySQL server is reachable.
 * Stores database records in a persistent directory across application launches.
 */
public final class EmbeddedDatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDatabaseManager.class);

    private static final String DATABASE_NAME = "nextit";
    private static final String CONNECTION_PARAMETERS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
    }

    private static DB embeddedDatabase;
    private static boolean started;

    private EmbeddedDatabaseManager() {}

    public static synchronized boolean isStarted() {
        return started;
    }

    public static void main(String[] args) throws Exception {
        ensureStarted();
        System.out.println("URL: " + DatabaseConfig.url());
        try (Connection c = DatabaseConfig.getConnection()) {
            System.out.println("Connected to: " + c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
        }
        stop();
    }

    public static boolean shouldFallback(String url) {
        return !started;
    }

    /**
     * Resolves a persistent storage directory on disk for the embedded database.
     * Prioritizes NEXTIT_DATA_DIR, followed by %LOCALAPPDATA%\NextIT\data, %APPDATA%\NextIT\data,
     * and finally ~/.nextit/data.
     */
    public static Path resolveDataDirectory() {
        String customDir = System.getProperty("NEXTIT_DATA_DIR");
        if (customDir == null || customDir.isBlank()) {
            customDir = System.getenv("NEXTIT_DATA_DIR");
        }
        if (customDir != null && !customDir.isBlank()) {
            Path path = Path.of(customDir.trim()).toAbsolutePath();
            try {
                Files.createDirectories(path);
                return path;
            } catch (IOException e) {
                LOG.warn("Failed to create custom data directory {}, falling back to default", path, e);
            }
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            Path path = Path.of(localAppData, "NextIT", "data").toAbsolutePath();
            try {
                Files.createDirectories(path);
                return path;
            } catch (IOException ignored) {}
        }

        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            Path path = Path.of(appData, "NextIT", "data").toAbsolutePath();
            try {
                Files.createDirectories(path);
                return path;
            } catch (IOException ignored) {}
        }

        String userHome = System.getProperty("user.home", ".");
        Path fallback = Path.of(userHome, ".nextit", "data").toAbsolutePath();
        try {
            Files.createDirectories(fallback);
        } catch (IOException ignored) {}
        return fallback;
    }

    /**
     * Custom DB instance that enforces persistence:
     * 1. Overrides prepareDirectories to prevent deletion of dataDir even if in temp paths.
     * 2. Overrides cleanupOnExit to prevent MariaDB4j from deleting the database on JVM exit.
     * 3. Skips mysql_install_db bootstrap when the persistent data directory is already initialized.
     */
    private static class PersistentEmbeddedDB extends DB {
        PersistentEmbeddedDB(DBConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected void prepareDirectories() throws ManagedProcessException {
            try {
                java.lang.reflect.Field baseDirF = DB.class.getDeclaredField("baseDir");
                java.lang.reflect.Field libDirF = DB.class.getDeclaredField("libDir");
                java.lang.reflect.Field tmpDirF = DB.class.getDeclaredField("tmpDir");
                java.lang.reflect.Field dataDirF = DB.class.getDeclaredField("dataDir");

                baseDirF.setAccessible(true);
                libDirF.setAccessible(true);
                tmpDirF.setAccessible(true);
                dataDirF.setAccessible(true);

                baseDirF.set(this, ch.vorburger.mariadb4j.Util.getDirectory(configuration.getBaseDir()));
                libDirF.set(this, ch.vorburger.mariadb4j.Util.getDirectory(configuration.getLibDir()));
                tmpDirF.set(this, ch.vorburger.mariadb4j.Util.getDirectory(configuration.getTmpDir()));
                dataDirF.set(this, ch.vorburger.mariadb4j.Util.getDirectory(configuration.getDataDir()));
            } catch (Exception e) {
                throw new ManagedProcessException("Failed to prepare persistent directories", e);
            }
        }

        @Override
        protected void cleanupOnExit() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    LOG.info("JVM shutdown: cleanly terminating persistent embedded MariaDB process...");
                    PersistentEmbeddedDB.this.stop();
                } catch (Exception e) {
                    LOG.debug("Notice while stopping database on JVM exit: {}", e.getMessage());
                }
            }, "nextit-persistent-mariadb-shutdown"));
        }

        static PersistentEmbeddedDB create(DBConfiguration config) throws ManagedProcessException {
            PersistentEmbeddedDB db = new PersistentEmbeddedDB(config);
            db.prepareDirectories();
            db.unpackEmbeddedDb();
            File dataDir = config.getDataDir();
            File mysqlSystemDir = (dataDir != null) ? new File(dataDir, "mysql") : null;
            if (mysqlSystemDir == null || !mysqlSystemDir.isDirectory()) {
                LOG.info("First-time embedded database setup: initializing system tables in {}...",
                        dataDir != null ? dataDir.getAbsolutePath() : "default dir");
                db.install();
            } else {
                LOG.info("Existing database detected at {}: skipping install step to preserve stored user data.",
                        dataDir.getAbsolutePath());
            }
            return db;
        }
    }

    /**
     * Scans for and terminates any orphaned MariaDB mysqld.exe processes that may have
     * lingered from unexpected application terminations, freeing locks on data files like ibdata1.
     */
    public static void killOrphanedMysqldProcesses(Path dataDir) {
        if (System.getProperty("nextit.test.active") != null) {
            return;
        }
        try {
            long currentPid = ProcessHandle.current().pid();
            String targetDataDirStr = (dataDir != null) ? dataDir.toAbsolutePath().toString().toLowerCase() : "";

            ProcessHandle.allProcesses()
                    .filter(p -> p.pid() != currentPid)
                    .filter(p -> {
                        String cmd = p.info().command().orElse("").toLowerCase();
                        if (!cmd.endsWith("mysqld.exe") || !cmd.contains("mariadb4j")) {
                            return false;
                        }
                        String line = p.info().commandLine().orElse("").toLowerCase();
                        if (!line.isBlank()) {
                            return (!targetDataDirStr.isBlank() && line.contains(targetDataDirStr))
                                    || line.contains("nextit\\data");
                        }
                        return true;
                    })
                    .forEach(p -> {
                        LOG.warn("Terminating orphaned MariaDB process [PID {}]", p.pid());
                        p.destroyForcibly();
                    });
            Thread.sleep(400);
        } catch (Exception e) {
            LOG.debug("Notice during orphaned process check: {}", e.getMessage());
        }
    }

    private static void cleanupStalePidFiles(Path dataDir) {
        if (dataDir == null || !Files.isDirectory(dataDir)) return;
        try (var stream = Files.newDirectoryStream(dataDir, "*.pid")) {
            for (Path pidFile : stream) {
                try {
                    Files.deleteIfExists(pidFile);
                    LOG.info("Removed stale database PID lock: {}", pidFile.getFileName());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void ensureStarted() throws SQLException {
        if (started) return;

        Path dataDir = resolveDataDirectory();
        killOrphanedMysqldProcesses(dataDir);

        LOG.info("Starting persistent local embedded database...");
        try {
            cleanupStalePidFiles(dataDir);
            LOG.info("Persistent database storage location: {}", dataDir.toAbsolutePath());

            DBConfigurationBuilder builder = DBConfigurationBuilder.newBuilder();
            builder.setDataDir(dataDir.toFile());
            builder.setDeletingTemporaryBaseAndDataDirsOnShutdown(false);
            builder.setPort(0);
            DBConfiguration configuration = builder.build();

            embeddedDatabase = PersistentEmbeddedDB.create(configuration);
            embeddedDatabase.start();

            String serverUrl = "jdbc:mysql://127.0.0.1:" + configuration.getPort() + "/";
            initializeSqlScript(serverUrl, "schema.sql");
            ensureSchemaMigrations(serverUrl);
            if (!isDatabasePopulated(serverUrl)) {
                initializeSqlScript(serverUrl, "seed.sql");
                LOG.info("Embedded database initialized with demo seed data.");
            } else {
                LOG.info("Embedded database already contains user data; skipping seed script to preserve state.");
            }

            String fullUrl = serverUrl + DATABASE_NAME + CONNECTION_PARAMETERS;
            System.setProperty("NEXTIT_DB_URL", fullUrl);
            System.setProperty("NEXTIT_DB_USER", "root");
            System.setProperty("NEXTIT_DB_PASSWORD", "");

            Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedDatabaseManager::stop, "nextit-embedded-db-shutdown"));
            started = true;
            LOG.info("Embedded database initialized successfully on port {}.", configuration.getPort());
        } catch (Exception e) {
            stop();
            throw new SQLException("Failed to start embedded database: " + e.getMessage(), e);
        }
    }

    public static synchronized void stop() {
        if (embeddedDatabase != null) {
            try {
                embeddedDatabase.stop();
            } catch (Exception e) {
                LOG.warn("Error stopping embedded database: {}", e.getMessage());
            } finally {
                embeddedDatabase = null;
                started = false;
            }
        }
    }

    private static void ensureSchemaMigrations(String serverUrl) {
        String dbUrl = serverUrl + DATABASE_NAME + CONNECTION_PARAMETERS;
        try (Connection c = DriverManager.getConnection(dbUrl, "root", "");
             Statement st = c.createStatement()) {

            // 1. Ensure employers table has job_title
            try {
                st.execute("ALTER TABLE employers ADD COLUMN IF NOT EXISTS job_title VARCHAR(100) NULL");
                LOG.info("Verified employers.job_title schema column");
            } catch (Exception ex) {
                LOG.debug("Notice adding job_title to employers: {}", ex.getMessage());
            }

            // 2. Ensure users table has verification and security columns
            String[] userCols = {
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED'",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS rejection_reason TEXT NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS verified_at DATETIME NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until DATETIME NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE"
            };
            for (String sql : userCols) {
                try {
                    st.execute(sql);
                } catch (Exception ignored) {}
            }

            // 3. Ensure assigned_role_ids table exists
            try {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS assigned_role_ids (
                        role_id VARCHAR(50) NOT NULL PRIMARY KEY,
                        role VARCHAR(20) NOT NULL,
                        user_id INT NULL,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_assigned_role_id (role_id)
                    )
                """);
            } catch (Exception ex) {
                LOG.debug("Notice ensuring assigned_role_ids: {}", ex.getMessage());
            }
        } catch (Exception e) {
            LOG.warn("Notice during embedded schema migration: {}", e.getMessage());
        }
    }

    private static boolean isDatabasePopulated(String serverUrl) {
        String testUrl = serverUrl + DATABASE_NAME + CONNECTION_PARAMETERS;
        try (Connection c = DriverManager.getConnection(testUrl, "root", "");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void initializeSqlScript(String serverUrl, String fileName) throws SQLException {
        String script = null;
        Path file = Path.of(System.getProperty("user.dir"), "database", fileName);
        if (Files.isRegularFile(file)) {
            try {
                script = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new SQLException("Unable to read " + file.toAbsolutePath(), e);
            }
        } else {
            // Check app subfolder or classpath resource
            Path appFile = Path.of(System.getProperty("user.dir"), "app", "database", fileName);
            if (Files.isRegularFile(appFile)) {
                try {
                    script = Files.readString(appFile, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new SQLException("Unable to read " + appFile.toAbsolutePath(), e);
                }
            } else {
                try (var is = EmbeddedDatabaseManager.class.getResourceAsStream("/database/" + fileName)) {
                    if (is != null) {
                        script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } catch (IOException e) {
                    throw new SQLException("Unable to read embedded resource /database/" + fileName, e);
                }
            }
        }

        if (script == null) {
            LOG.warn("Database script not found on disk or classpath: {}", fileName);
            return;
        }

        try (Connection connection = DriverManager.getConnection(serverUrl + CONNECTION_PARAMETERS, "root", "");
             Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                try {
                    statement.execute(sql);
                } catch (SQLException ex) {
                    LOG.debug("Notice while executing statement in {}: {}", fileName, ex.getMessage());
                }
            }
        }
    }

    private static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder(script.length());
        for (String line : script.split("\\R")) {
            if (line.stripLeading().startsWith("--")) continue;
            withoutComments.append(line).append('\n');
        }

        List<String> statements = new ArrayList<>();
        for (String candidate : withoutComments.toString().split(";")) {
            String sql = candidate.strip();
            if (!sql.isEmpty()) statements.add(sql);
        }
        return statements;
    }
}
