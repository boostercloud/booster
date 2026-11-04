package io.boostercloud.booster.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.CommandReceipt;
import io.boostercloud.booster.core.CommitResult;
import io.boostercloud.booster.core.EventStore;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.OptimisticConcurrencyException;
import io.boostercloud.booster.core.PendingEvent;
import io.boostercloud.booster.core.StoredEvent;
import io.boostercloud.booster.core.StreamId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Durable H2 journal for the local provider. A commit atomically checks stream versions,
 * appends events and records its idempotency receipt. A database row lock serializes writers,
 * including other instances, so global positions never expose gaps from uncommitted writes.
 *
 * <p>The file constructor takes a base filename; H2 appends {@code .mv.db}. All connections
 * are owned by this store. Closing it waits for its current operations and does not shut down
 * other stores using the same database. File databases support one JVM at a time; use a
 * server database provider for distributed deployments.
 */
public final class JdbcEventStore implements EventStore {
  private static final int SCHEMA_VERSION = 1;
  private static final int MAX_PAGE_SIZE = 10_000;
  private static final Object SCHEMA_LOCK = new Object();
  private final String url;
  private final Connection anchor;
  private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
  private boolean closed;

  public JdbcEventStore(Path databaseFile) {
    this(fileUrl(databaseFile));
  }

  private JdbcEventStore(String url) {
    this.url = url;
    Connection opened = null;
    try {
      opened = connection();
      synchronized (SCHEMA_LOCK) {
        initialize(opened);
      }
      anchor = opened;
    } catch (SQLException | RuntimeException failure) {
      if (opened != null) {
        try { opened.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
      }
      throw failure instanceof RuntimeException runtime ? runtime : storageFailure(failure);
    }
  }

  /** Creates a private in-memory database which exists until the last store closes. */
  public static JdbcEventStore inMemory() {
    return inMemory("booster_" + UUID.randomUUID().toString().replace("-", ""));
  }

  /** Stores with the same name share a database within this JVM. */
  public static JdbcEventStore inMemory(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("Database name must contain 1–128 letters, digits, underscores or hyphens");
    }
    return new JdbcEventStore("jdbc:h2:mem:" + name + ";LOCK_TIMEOUT=10000");
  }

  @Override
  public Optional<CommandReceipt> findCommand(String requestId) {
    validateText(requestId, "Request ID", 512);
    return withConnection(connection -> findCommand(connection, requestId));
  }

  @Override
  public CommitResult commit(String requestId, String fingerprint,
      Map<StreamId, Long> expectedVersions, List<PendingEvent> events, JsonNode result) {
    return commitInternal(requestId, fingerprint, expectedVersions, events, result, null);
  }

  @Override public CommitResult commitAtPosition(String requestId, String fingerprint,
      Map<StreamId,Long> expectedVersions, List<PendingEvent> events, JsonNode result, long expectedPosition) {
    if (expectedPosition < 0) throw new IllegalArgumentException("Negative expected journal position");
    return commitInternal(requestId, fingerprint, expectedVersions, events, result, expectedPosition);
  }

  private CommitResult commitInternal(String requestId, String fingerprint, Map<StreamId,Long> expectedVersions,
      List<PendingEvent> events, JsonNode result, Long expectedPosition) {
    validateText(requestId, "Request ID", 512);
    validateText(fingerprint, "Fingerprint", 1024);
    Objects.requireNonNull(expectedVersions, "expectedVersions");
    Objects.requireNonNull(events, "events");
    Objects.requireNonNull(result, "result");
    Map<StreamId, Long> expected = Map.copyOf(expectedVersions);
    List<PendingEvent> pending = List.copyOf(events);
    for (var entry : expected.entrySet()) {
      validateStream(entry.getKey());
      if (entry.getValue() < 0) throw new IllegalArgumentException("Expected versions must not be negative");
    }
    var eventIds = new HashSet<String>();
    for (PendingEvent event : pending) {
      validateText(event.id(), "Event ID", 255);
      validateStream(event.stream());
      if (!expected.containsKey(event.stream())) {
        throw new IllegalArgumentException("Every event stream requires an expected version");
      }
      if (!eventIds.add(event.id())) throw conflict("Duplicate event ID in commit");
    }
    String encodedResult = encode(result);
    List<String> encodedEvents = pending.stream().map(JdbcEventStore::encode).toList();
    return withConnection(connection -> {
      connection.setAutoCommit(false);
      try {
        long position = lockJournal(connection);
        Optional<CommandReceipt> existing = findCommand(connection, requestId);
        if (existing.isPresent()) {
          if (!existing.get().fingerprint().equals(fingerprint)) {
            throw conflict("Request ID already belongs to another command");
          }
          connection.commit();
          return new CommitResult(true, existing.get());
        }
        if (expectedPosition != null && position != expectedPosition) throw new OptimisticConcurrencyException("Journal changed since validation");
        Map<StreamId, Long> versions = new HashMap<>();
        for (var entry : expected.entrySet()) {
          long actual = version(connection, entry.getKey());
          if (actual != entry.getValue()) {
            throw new OptimisticConcurrencyException(entry.getKey(), entry.getValue(), actual);
          }
          versions.put(entry.getKey(), actual);
        }
        long firstPosition = position + 1;
        List<StoredEvent> committed = new ArrayList<>();
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO booster_events (global_position, stream_type, stream_id, stream_version, event_id, event_json) VALUES (?, ?, ?, ?, ?, ?)")) {
          for (int index = 0; index < pending.size(); index++) {
            PendingEvent event = pending.get(index);
            long streamVersion = Math.addExact(versions.get(event.stream()), 1);
            position = Math.addExact(position, 1);
            insert.setLong(1, position);
            insert.setString(2, event.stream().entityType());
            insert.setString(3, event.stream().entityId());
            insert.setLong(4, streamVersion);
            insert.setString(5, event.id());
            insert.setString(6, encodedEvents.get(index));
            insert.executeUpdate();
            versions.put(event.stream(), streamVersion);
            committed.add(new StoredEvent(position, streamVersion, event));
          }
        }
        try (PreparedStatement update = connection.prepareStatement(
            "MERGE INTO booster_streams (stream_type, stream_id, stream_version) KEY (stream_type, stream_id) VALUES (?, ?, ?)")) {
          for (var entry : versions.entrySet()) {
            update.setString(1, entry.getKey().entityType());
            update.setString(2, entry.getKey().entityId());
            update.setLong(3, entry.getValue());
            update.executeUpdate();
          }
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO booster_commands (request_id, fingerprint, result_json, first_position, last_position) VALUES (?, ?, ?, ?, ?)")) {
          insert.setString(1, requestId);
          insert.setString(2, fingerprint);
          insert.setString(3, encodedResult);
          insert.setLong(4, firstPosition);
          insert.setLong(5, position);
          insert.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE booster_journal SET last_position = ? WHERE singleton_id = 1")) {
          update.setLong(1, position);
          update.executeUpdate();
        }
        connection.commit();
        return new CommitResult(false, new CommandReceipt(requestId, fingerprint, result, committed));
      } catch (SQLException | RuntimeException failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
        if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
          throw new BoosterException(BoosterException.Code.CONFLICT, "Event or request identifier already exists", sql);
        }
        throw failure;
      }
    });
  }

  @Override
  public List<StoredEvent> readStream(StreamId stream, long afterVersion) {
    validateStream(stream);
    if (afterVersion < 0) throw new IllegalArgumentException("Stream version must not be negative");
    return withConnection(connection -> {
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT global_position, stream_version, event_json FROM booster_events WHERE stream_type = ? AND stream_id = ? AND stream_version > ? ORDER BY stream_version")) {
        query.setString(1, stream.entityType());
        query.setString(2, stream.entityId());
        query.setLong(3, afterVersion);
        return readEvents(query);
      }
    });
  }

  @Override
  public List<StoredEvent> readAll(long afterPosition, int limit) {
    if (afterPosition < 0) throw new IllegalArgumentException("Journal position must not be negative");
    if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
    return withConnection(connection -> {
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT global_position, stream_version, event_json FROM booster_events WHERE global_position > ? ORDER BY global_position FETCH FIRST ? ROWS ONLY")) {
        query.setLong(1, afterPosition);
        query.setInt(2, limit);
        return readEvents(query);
      }
    });
  }

  @Override
  public boolean redactEventPayload(String eventId, java.time.Instant deletedAt) {
    validateText(eventId, "Event ID", 255); Objects.requireNonNull(deletedAt);
    return withConnection(connection -> {
      connection.setAutoCommit(false);
      try {
        lockJournal(connection);
        try (var query = connection.prepareStatement("SELECT event_json FROM booster_events WHERE event_id=?")) {
          query.setString(1, eventId);
          try (var rows = query.executeQuery()) {
            if (!rows.next()) { connection.commit(); return false; }
            PendingEvent original;
            try { original = Json.MAPPER.readValue(rows.getString(1), PendingEvent.class); }
            catch (JsonProcessingException error) { throw storageFailure(error); }
            if (original.metadata().containsKey("deletedAt") && original.payload().isObject() && original.payload().isEmpty()) { connection.commit(); return true; }
            var redacted = original.redacted(deletedAt);
            try (var update = connection.prepareStatement("UPDATE booster_events SET event_json=? WHERE event_id=?")) {
              update.setString(1, encode(redacted)); update.setString(2, eventId); update.executeUpdate();
            }
          }
        }
        // Receipts reference the journal position range, so reads immediately observe the redaction.
        connection.commit(); return true;
      } catch (SQLException | RuntimeException error) {
        try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
        throw error;
      }
    });
  }

  @Override
  public long lastPosition() {
    return withConnection(connection -> {
      try (Statement query = connection.createStatement();
          ResultSet rows = query.executeQuery("SELECT last_position FROM booster_journal WHERE singleton_id = 1")) {
        if (!rows.next()) throw new SQLException("Journal state is missing");
        return rows.getLong(1);
      }
    });
  }

  @Override
  public void close() {
    lifecycle.writeLock().lock();
    try {
      if (!closed) {
        closed = true;
        try { anchor.close(); } catch (SQLException failure) { throw storageFailure(failure); }
      }
    } finally {
      lifecycle.writeLock().unlock();
    }
  }

  private static String fileUrl(Path input) {
    Path path = Objects.requireNonNull(input, "databaseFile").toAbsolutePath().normalize();
    String filename = path.toString();
    if (filename.contains(";") || filename.contains("\n") || filename.contains("\r")) {
      throw new IllegalArgumentException("Database path must not contain JDBC parameter separators");
    }
    try {
      Files.createDirectories(path.getParent());
    } catch (IOException failure) {
      throw storageFailure(failure);
    }
    return "jdbc:h2:file:" + filename + ";LOCK_TIMEOUT=10000;WRITE_DELAY=0";
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(url, "sa", "");
  }

  private static void initialize(Connection connection) throws SQLException {
    try (Statement ddl = connection.createStatement()) {
      ddl.execute("CREATE TABLE IF NOT EXISTS booster_schema (singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), schema_version INTEGER NOT NULL)");
      try (ResultSet version = ddl.executeQuery("SELECT schema_version FROM booster_schema WHERE singleton_id = 1")) {
        if (version.next() && version.getInt(1) != SCHEMA_VERSION) {
          throw new BoosterException(BoosterException.Code.INTERNAL, "Unsupported event store schema version");
        }
      }
      ddl.execute("CREATE TABLE IF NOT EXISTS booster_journal (singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), last_position BIGINT NOT NULL CHECK (last_position >= 0))");
      ddl.execute("CREATE TABLE IF NOT EXISTS booster_streams (stream_type VARCHAR(255) NOT NULL, stream_id VARCHAR(1024) NOT NULL, stream_version BIGINT NOT NULL CHECK (stream_version >= 0), PRIMARY KEY (stream_type, stream_id))");
      ddl.execute("CREATE TABLE IF NOT EXISTS booster_events (global_position BIGINT PRIMARY KEY, stream_type VARCHAR(255) NOT NULL, stream_id VARCHAR(1024) NOT NULL, stream_version BIGINT NOT NULL, event_id VARCHAR(255) NOT NULL UNIQUE, event_json CLOB NOT NULL, UNIQUE (stream_type, stream_id, stream_version))");
      ddl.execute("CREATE TABLE IF NOT EXISTS booster_commands (request_id VARCHAR(512) PRIMARY KEY, fingerprint VARCHAR(1024) NOT NULL, result_json CLOB NOT NULL, first_position BIGINT NOT NULL, last_position BIGINT NOT NULL)");
      ddl.execute("INSERT INTO booster_journal SELECT 1, 0 WHERE NOT EXISTS (SELECT 1 FROM booster_journal WHERE singleton_id = 1)");
      ddl.execute("INSERT INTO booster_schema SELECT 1, " + SCHEMA_VERSION + " WHERE NOT EXISTS (SELECT 1 FROM booster_schema WHERE singleton_id = 1)");
    }
  }

  private static long lockJournal(Connection connection) throws SQLException {
    try (Statement query = connection.createStatement(); ResultSet row = query.executeQuery(
        "SELECT last_position FROM booster_journal WHERE singleton_id = 1 FOR UPDATE")) {
      if (!row.next()) throw new SQLException("Journal state is missing");
      return row.getLong(1);
    }
  }

  private static long version(Connection connection, StreamId stream) throws SQLException {
    try (PreparedStatement query = connection.prepareStatement(
        "SELECT stream_version FROM booster_streams WHERE stream_type = ? AND stream_id = ?")) {
      query.setString(1, stream.entityType());
      query.setString(2, stream.entityId());
      try (ResultSet rows = query.executeQuery()) {
        return rows.next() ? rows.getLong(1) : 0;
      }
    }
  }

  private static Optional<CommandReceipt> findCommand(Connection connection, String requestId) throws SQLException {
    try (PreparedStatement query = connection.prepareStatement(
        "SELECT fingerprint, result_json, first_position, last_position FROM booster_commands WHERE request_id = ?")) {
      query.setString(1, requestId);
      try (ResultSet row = query.executeQuery()) {
        if (!row.next()) return Optional.empty();
        List<StoredEvent> events;
        try (PreparedStatement eventQuery = connection.prepareStatement(
            "SELECT global_position, stream_version, event_json FROM booster_events WHERE global_position BETWEEN ? AND ? ORDER BY global_position")) {
          eventQuery.setLong(1, row.getLong(3));
          eventQuery.setLong(2, row.getLong(4));
          events = readEvents(eventQuery);
        }
        try {
          return Optional.of(new CommandReceipt(requestId, row.getString(1), Json.MAPPER.readTree(row.getString(2)), events));
        } catch (JsonProcessingException failure) {
          throw storageFailure(failure);
        }
      }
    }
  }

  private static List<StoredEvent> readEvents(PreparedStatement query) throws SQLException {
    List<StoredEvent> events = new ArrayList<>();
    try (ResultSet rows = query.executeQuery()) {
      while (rows.next()) {
        try {
          events.add(new StoredEvent(rows.getLong(1), rows.getLong(2),
              Json.MAPPER.readValue(rows.getString(3), PendingEvent.class)));
        } catch (JsonProcessingException failure) {
          throw storageFailure(failure);
        }
      }
    }
    return List.copyOf(events);
  }

  private <T> T withConnection(SqlOperation<T> operation) {
    lifecycle.readLock().lock();
    try {
      if (closed) throw new IllegalStateException("Event store is closed");
      try (Connection connection = connection()) {
        return operation.run(connection);
      } catch (SQLException failure) {
        throw storageFailure(failure);
      }
    } finally {
      lifecycle.readLock().unlock();
    }
  }

  private static void validateStream(StreamId stream) {
    Objects.requireNonNull(stream, "stream");
    validateText(stream.entityType(), "Entity type", 255);
    validateText(stream.entityId(), "Entity ID", 1024);
  }

  private static void validateText(String text, String label, int maxLength) {
    if (text == null || text.isBlank() || text.length() > maxLength) {
      throw new IllegalArgumentException(label + " must be nonblank and at most " + maxLength + " characters");
    }
  }

  private static String encode(Object value) {
    try { return Json.MAPPER.writeValueAsString(value); }
    catch (JsonProcessingException failure) { throw storageFailure(failure); }
  }

  private static BoosterException conflict(String message) {
    return new BoosterException(BoosterException.Code.CONFLICT, message);
  }

  private static BoosterException storageFailure(Throwable failure) {
    return new BoosterException(BoosterException.Code.INTERNAL, "Event store operation failed", failure);
  }

  @FunctionalInterface
  private interface SqlOperation<T> {
    T run(Connection connection) throws SQLException;
  }
}
