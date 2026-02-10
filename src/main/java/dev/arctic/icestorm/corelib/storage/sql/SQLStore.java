package dev.arctic.icestorm.corelib.storage.sql;

import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class SQLStore {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final StorageDatabase database;
    private final String prefix;

    private String table(String tableName) {
        String safePrefix = safeIdentifier(prefix, "prefix");
        String safeTable = safeIdentifier(tableName, "tableName");
        return safePrefix + "_" + safeTable;
    }

    public void ensureTable(String tableName) throws SQLException {
        String table = table(tableName);

        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id VARCHAR(64) PRIMARY KEY," +
                "value TEXT NOT NULL," +
                "updated_at BIGINT NOT NULL" +
                ");";

        try (Connection connection = database.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    public Optional<String> get(String tableName, String id) throws SQLException {
        String table = table(tableName);
        String safeId = requireNonBlank(id, "id");

        ensureTable(tableName);

        String sql = "SELECT value FROM " + table + " WHERE id = ? LIMIT 1;";

        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String value = resultSet.getString("value");
                return Optional.ofNullable(value).filter(v -> !v.isBlank());
            }
        }
    }

    public void put(String tableName, String id, String value) throws SQLException {
        String table = table(tableName);
        String safeId = requireNonBlank(id, "id");
        String safeValue = requireNonBlank(value, "value");

        ensureTable(tableName);

        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql(connection, table))) {

            statement.setString(1, safeId);
            statement.setString(2, safeValue);
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        }
    }

    public int putBatch(String tableName, Map<String, String> values) throws SQLException {
        String table = table(tableName);
        Objects.requireNonNull(values, "values");

        if (values.isEmpty()) {
            return 0;
        }

        ensureTable(tableName);

        try (Connection connection = database.getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                long updatedAt = Instant.now().getEpochSecond();
                int total = 0;

                try (PreparedStatement statement = connection.prepareStatement(upsertSql(connection, table))) {
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        String id = entry.getKey();
                        String value = entry.getValue();

                        if (id == null || id.isBlank()) continue;
                        if (value == null || value.isBlank()) continue;

                        statement.setString(1, id);
                        statement.setString(2, value);
                        statement.setLong(3, updatedAt);
                        statement.addBatch();
                    }

                    int[] results = statement.executeBatch();
                    for (int r : results) {
                        if (r > 0) total += r;
                    }
                }

                connection.commit();
                return total;
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Batch put failed.", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public CompletableFuture<Optional<String>> getAsync(String tableName, String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get(tableName, id);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, database.getExecutorService());
    }

    public CompletableFuture<Void> putAsync(String tableName, String id, String value) {
        return CompletableFuture.runAsync(() -> {
            try {
                put(tableName, id, value);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, database.getExecutorService());
    }

    public CompletableFuture<Integer> putBatchAsync(String tableName, Map<String, String> values) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return putBatch(tableName, values);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, database.getExecutorService());
    }

    private static String upsertSql(Connection connection, String table) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);

        if (product.contains("mysql") || product.contains("mariadb")) {
            return "INSERT INTO " + table + " (id, value, updated_at) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at);";
        }

        return "INSERT INTO " + table + " (id, value, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at;";
    }

    private static String safeIdentifier(String identifier, String field) {
        Objects.requireNonNull(identifier, field);
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(field + " must match [A-Za-z_][A-Za-z0-9_]*");
        }
        return identifier;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
