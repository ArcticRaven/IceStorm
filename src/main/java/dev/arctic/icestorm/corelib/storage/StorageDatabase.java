package dev.arctic.icestorm.corelib.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public final class StorageDatabase implements AutoCloseable {

    private final String name;
    private final HikariDataSource dataSource;
    private final ExecutorService executorService;

    private StorageDatabase(String name, HikariDataSource dataSource, ExecutorService executorService) {
        this.name = requireNonBlank(name, "name");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
    }

    public static StorageDatabase sqlite(String name, Path sqliteFile) {
        Objects.requireNonNull(sqliteFile, "sqliteFile");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setPoolName("sqlite-" + safeName(name));
        hikariConfig.setConnectionInitSql(
                "PRAGMA foreign_keys=ON; " +
                        "PRAGMA journal_mode=WAL; " +
                        "PRAGMA synchronous=NORMAL; " +
                        "PRAGMA temp_store=MEMORY;"
        );

        HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);

        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "db-" + safeName(name));
            thread.setDaemon(true);
            return thread;
        });

        return new StorageDatabase(name, hikariDataSource, executorService);
    }

    public static StorageDatabase mysql(String name, String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(requireNonBlank(jdbcUrl, "jdbcUrl"));
        hikariConfig.setUsername(requireNonBlank(username, "username"));
        hikariConfig.setPassword(requireNonBlank(password, "password"));
        hikariConfig.setMaximumPoolSize(Math.max(2, poolSize));
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setPoolName("mysql-" + safeName(name));

        HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);

        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "db-" + safeName(name));
            thread.setDaemon(true);
            return thread;
        });

        return new StorageDatabase(name, hikariDataSource, executorService);
    }

    /**
     * Creates a submodule store that shares this DB but uses a prefix like "claims_*" to avoid collisions.
     */
    public SQLStore store(String modulePrefix) {
        return new SQLStore(this, modulePrefix);
    }

    @Override
    public void close() {
        executorService.shutdown();
        dataSource.close();
    }

    private static String safeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
