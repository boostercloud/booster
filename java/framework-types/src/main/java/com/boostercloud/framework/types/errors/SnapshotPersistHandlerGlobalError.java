package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;

/**
 * Context of a snapshot persistence error (deprecated, EOL v3: errors returned by the hook are logged and
 * ignored); hook {@code onSnapshotPersistError(error, snapshot)}. Port of {@code SnapshotPersistHandlerGlobalError}; the original error is the last constructor argument.
 */
public final class SnapshotPersistHandlerGlobalError extends GlobalErrorContainer {
  private final NonPersistedEntitySnapshotEnvelope snapshot;

  /**
   * @param snapshot the snapshot that could not be stored
   * @param originalError the error that happened
   */
  public SnapshotPersistHandlerGlobalError(NonPersistedEntitySnapshotEnvelope snapshot, Throwable originalError) {
    super(originalError);
    this.snapshot = snapshot;
  }

  /**
   * @return the snapshot that could not be stored
   */
  public NonPersistedEntitySnapshotEnvelope snapshot() {
    return snapshot;
  }
}
