package com.boostercloud.framework.core.services;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.Optional;

/**
 * Port of {@code services/event-store.ts}: rebuilds the current snapshot of an entity (latest snapshot + the events
 * stored after it, reduced one by one, including the built-in {@code BoosterEntityMigrated}/{@code
 * BoosterEntityTouched} reductions), persists the new snapshot with retries, and marks events as dispatched.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.1). Trace points ({@code FETCH_ENTITY_SNAPSHOT},
 * {@code CUSTOM}, {@code STORE_SNAPSHOT}, {@code LOAD_LATEST_SNAPSHOT}, {@code LOAD_EVENT_STREAM_SINCE},
 * {@code ENTITY_REDUCER}) go through {@code Tracer.trace}.
 */
public class EventStore {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public EventStore(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code fetchEntitySnapshot(entityName, entityID)}. Implemented by stage core-2a.
   *
   * @param entityName the entity class name
   * @param entityID the entity id
   * @return the current snapshot, empty when the entity has no snapshot and no events
   */
  public Optional<EntitySnapshotEnvelope> fetchEntitySnapshot(String entityName, UUID entityID) {
    throw new UnsupportedOperationException("not implemented yet: EventStore#fetchEntitySnapshot");
  }

  /**
   * {@code storeDispatchedEvent(eventEnvelope)}: records that the event was dispatched. Implemented by stage core-2a.
   *
   * @param eventEnvelope the event
   * @return {@code true} when stored, {@code false} when it had already been dispatched
   */
  public boolean storeDispatchedEvent(EventEnvelope eventEnvelope) {
    throw new UnsupportedOperationException("not implemented yet: EventStore#storeDispatchedEvent");
  }
}
