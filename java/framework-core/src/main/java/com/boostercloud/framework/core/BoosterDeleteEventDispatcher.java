package com.boostercloud.framework.core;

import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import java.util.List;

/**
 * Port of {@code booster-delete-event-dispatcher.ts} ({@code Booster.deleteEvent}): deletes events and the snapshots
 * built from them.
 *
 * <p>Strictly sequential, with no error handling, logging or authorization (errors propagate and leave the work
 * done so far): {@code findDeletableEvent(config, parameters)}; nothing found → {@code false}. Otherwise, for each
 * event in order: {@code findDeletableSnapshot(config, <the event's entityID, entityTypeName, createdAt>)}, each
 * snapshot is un-projected ({@code ReadModelStore.project(snapshot, true)}), then
 * {@code deleteSnapshot(config, snapshots)} (also with an empty list); finally one
 * {@code deleteEvent(config, events)} with every event, and {@code true}. What deleting means is up to the provider.
 */
public final class BoosterDeleteEventDispatcher {

  private BoosterDeleteEventDispatcher() {}

  /**
   * {@code deleteEvent(config, parameters)}.
   *
   * @param config the configuration
   * @param parameters which event
   * @return {@code false} when nothing was found, {@code true} after deleting
   */
  public static boolean deleteEvent(BoosterConfig config, EventDeleteParameters parameters) {
    return deleteEvent(config, parameters, new ReadModelStore(config));
  }

  /**
   * {@link #deleteEvent(BoosterConfig, EventDeleteParameters)} with the given read model store (for tests).
   *
   * @param config the configuration
   * @param parameters which event
   * @param readModelStore the read model store
   * @return {@code false} when nothing was found, {@code true} after deleting
   */
  static boolean deleteEvent(BoosterConfig config, EventDeleteParameters parameters, ReadModelStore readModelStore) {
    ProviderEventsLibrary provider = config.provider().events();
    List<EventEnvelope> events = provider.findDeletableEvent(config, parameters);
    if (events == null || events.isEmpty()) {
      return false;
    }
    for (EventEnvelope event : events) {
      // TypeScript passes the whole event envelope; the provider reads these three fields from it
      List<EntitySnapshotEnvelope> snapshots = provider.findDeletableSnapshot(config,
          new SnapshotDeleteParameters(event.entityID(), event.entityTypeName(), event.createdAt()));
      for (EntitySnapshotEnvelope snapshot : snapshots) {
        readModelStore.project(snapshot, true);
      }
      provider.deleteSnapshot(config, snapshots);
    }
    provider.deleteEvent(config, events);
    return true;
  }
}
