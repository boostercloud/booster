import { GlobalErrorContainer } from './global-error-container'
import { NonPersistedEntitySnapshotEnvelope } from '../envelope'

/**
 * @deprecated [EOL v3] Errors when persisting snapshots can be safely ignored because
 * this problem is likely caused by a temporary network error, and the events,
 * which are the true source of truth are kept in any case.
 * The next time the entity is fetched, the snapshot will be recalculated and
 * Booster will try to persist it once again.
 * If you define custom error handlers for this error, they will still be called,
 * but errors returned by them will be logged and ignored.
 * This class is kept for backwards compatibility.
 */
export class SnapshotPersistHandlerGlobalError extends GlobalErrorContainer {
  constructor(readonly snapshot: NonPersistedEntitySnapshotEnvelope, originalError: Error) {
    super(originalError)
  }
}
