package com.boostercloud.framework.provider.local.store;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Options of a {@link Datastore}, NeDB's {@code new Datastore({ filename, inMemoryOnly, corruptAlertThreshold })} plus
 * the Java store's own settings.
 *
 * @param filename the data file, or {@code null} for an in-memory datastore
 * @param corruptAlertThreshold fraction of corrupt lines above which loading fails (NeDB default {@code 0.1})
 * @param autocompactionLineThreshold compact automatically after a write once the file holds at least this many
 *     obsolete lines and more obsolete lines than live documents ({@code 0} disables it, NeDB's behaviour)
 * @param clock milliseconds since the epoch, used for TTL expiry and id generation
 * @param warnings receives recovery warnings (discarded torn lines, skipped corrupt lines, failed autocompactions)
 */
public record DatastoreOptions(
    Path filename,
    double corruptAlertThreshold,
    int autocompactionLineThreshold,
    LongSupplier clock,
    Consumer<String> warnings) {

  /** NeDB's default {@code corruptAlertThreshold}. */
  public static final double DEFAULT_CORRUPT_ALERT_THRESHOLD = 0.1;

  /** Canonical constructor with defaults for absent values. */
  public DatastoreOptions {
    clock = clock == null ? System::currentTimeMillis : clock;
    warnings = warnings == null ? message -> System.err.println("[Booster local store] " + message) : warnings;
  }

  /**
   * @param filename the data file
   * @return options with NeDB's defaults
   */
  public static DatastoreOptions of(Path filename) {
    return new DatastoreOptions(Objects.requireNonNull(filename, "filename"), DEFAULT_CORRUPT_ALERT_THRESHOLD, 0, null,
        null);
  }

  /**
   * @return options of an in-memory datastore ({@code inMemoryOnly: true})
   */
  public static DatastoreOptions inMemory() {
    return new DatastoreOptions(null, DEFAULT_CORRUPT_ALERT_THRESHOLD, 0, null, null);
  }

  /**
   * @param threshold the new threshold
   * @return a copy with another {@code corruptAlertThreshold}
   */
  public DatastoreOptions withCorruptAlertThreshold(double threshold) {
    return new DatastoreOptions(filename, threshold, autocompactionLineThreshold, clock, warnings);
  }

  /**
   * @param threshold obsolete lines that trigger an automatic compaction ({@code 0} disables it)
   * @return a copy with another {@code autocompactionLineThreshold}
   */
  public DatastoreOptions withAutocompactionLineThreshold(int threshold) {
    return new DatastoreOptions(filename, corruptAlertThreshold, threshold, clock, warnings);
  }

  /**
   * @param newClock milliseconds since the epoch
   * @return a copy with another clock
   */
  public DatastoreOptions withClock(LongSupplier newClock) {
    return new DatastoreOptions(filename, corruptAlertThreshold, autocompactionLineThreshold, newClock, warnings);
  }

  /**
   * @param listener receives warnings
   * @return a copy with another warnings listener
   */
  public DatastoreOptions withWarnings(Consumer<String> listener) {
    return new DatastoreOptions(filename, corruptAlertThreshold, autocompactionLineThreshold, clock, listener);
  }

  /**
   * @return whether the datastore lives in memory only
   */
  public boolean inMemoryOnly() {
    return filename == null;
  }
}
