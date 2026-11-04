package io.boostercloud.booster.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Durable local CAS documents. Atomic conditional writes also serialize independent instances. */
public final class JdbcStateStore implements StateStore {
  private final String url;
  private final Connection anchor;
  private boolean closed;
  public JdbcStateStore(Path file) { this(fileUrl(file)); }
  private JdbcStateStore(String url) {
    this.url = url;
    try {
      anchor = DriverManager.getConnection(url, "sa", "");
      try (var statement = anchor.createStatement()) {
        statement.execute("CREATE TABLE IF NOT EXISTS booster_state (namespace VARCHAR(255) NOT NULL, document_key VARCHAR(2048) NOT NULL, revision BIGINT NOT NULL, value_json CLOB NOT NULL, PRIMARY KEY(namespace, document_key))");
      }
    } catch (SQLException error) { throw failure(error); }
  }
  public static JdbcStateStore inMemory() { return inMemory("state_" + UUID.randomUUID().toString().replace("-", "")); }
  public static JdbcStateStore inMemory(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid state database name");
    return new JdbcStateStore("jdbc:h2:mem:" + name + ";LOCK_TIMEOUT=10000;WRITE_DELAY=0");
  }
  private static String fileUrl(Path file) {
    Objects.requireNonNull(file);
    Path absolute = file.toAbsolutePath().normalize();
    String value = absolute.toString();
    if (value.indexOf(';') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
      throw new IllegalArgumentException("Invalid database path");
    try { Files.createDirectories(absolute.getParent()); } catch (Exception error) { throw failure(error); }
    return "jdbc:h2:file:" + value + ";LOCK_TIMEOUT=10000;WRITE_DELAY=0";
  }
  private Connection connect() throws SQLException {
    if (closed) throw new IllegalStateException("State store is closed");
    return DriverManager.getConnection(url, "sa", "");
  }
  public synchronized Optional<StateDocument> get(String namespace, String key) {
    validate(namespace, key);
    try (var connection = connect(); var query = connection.prepareStatement(
        "SELECT revision,value_json FROM booster_state WHERE namespace=? AND document_key=?")) {
      query.setString(1, namespace); query.setString(2, key);
      try (var row = query.executeQuery()) {
        return row.next() ? Optional.of(new StateDocument(namespace, key, row.getLong(1), Json.MAPPER.readTree(row.getString(2)))) : Optional.empty();
      }
    } catch (Exception error) { if (error instanceof RuntimeException runtime) throw runtime; throw failure(error); }
  }
  public synchronized List<StateDocument> scan(String namespace, String afterKey, int limit) {
    validate(namespace, afterKey == null ? "_" : afterKey);
    if (limit < 1 || limit > 10000) throw new IllegalArgumentException("State page limit must be 1..10000");
    try (var connection = connect(); var query = connection.prepareStatement(
        "SELECT document_key,revision,value_json FROM booster_state WHERE namespace=? AND document_key>? ORDER BY document_key LIMIT ?")) {
      query.setString(1, namespace); query.setString(2, afterKey == null ? "" : afterKey); query.setInt(3, limit);
      var results = new ArrayList<StateDocument>();
      try (var rows = query.executeQuery()) {
        while (rows.next()) results.add(new StateDocument(namespace, rows.getString(1), rows.getLong(2), Json.MAPPER.readTree(rows.getString(3))));
      }
      return List.copyOf(results);
    } catch (Exception error) { if (error instanceof RuntimeException runtime) throw runtime; throw failure(error); }
  }
  public synchronized StateDocument put(String namespace, String key, long expectedVersion, JsonNode value) {
    validate(namespace, key);
    if (expectedVersion < 0) throw new IllegalArgumentException("Negative state version");
    var document = new StateDocument(namespace, key, Math.addExact(expectedVersion, 1), value);
    try (var connection = connect()) {
      if (expectedVersion == 0) {
        try (var insert = connection.prepareStatement("INSERT INTO booster_state(namespace,document_key,revision,value_json) VALUES(?,?,1,?)")) {
          insert.setString(1, namespace); insert.setString(2, key); insert.setString(3, document.value().toString()); insert.executeUpdate();
        }
      } else {
        try (var update = connection.prepareStatement("UPDATE booster_state SET revision=?,value_json=? WHERE namespace=? AND document_key=? AND revision=?")) {
          update.setLong(1, document.version()); update.setString(2, document.value().toString()); update.setString(3, namespace);
          update.setString(4, key); update.setLong(5, expectedVersion);
          if (update.executeUpdate() != 1) throw new OptimisticConcurrencyException("State changed: " + namespace + "/" + key);
        }
      }
      return document;
    } catch (SQLException error) {
      if ("23505".equals(error.getSQLState())) throw new OptimisticConcurrencyException("State changed: " + namespace + "/" + key);
      throw failure(error);
    }
  }
  private static void validate(String namespace, String key) {
    if (namespace == null || namespace.isBlank() || namespace.length() > 255 || key == null || key.isBlank() || key.length() > 2048)
      throw new IllegalArgumentException("Invalid state namespace or key");
  }
  private static BoosterException failure(Throwable error) { return new BoosterException(BoosterException.Code.INTERNAL, "State store operation failed", error); }
  public synchronized void close() {
    if (closed) return;
    closed = true;
    try { anchor.close(); } catch (SQLException error) { throw failure(error); }
  }
}
