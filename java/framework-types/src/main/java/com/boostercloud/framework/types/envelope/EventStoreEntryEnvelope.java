package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * An entry of the event store: an event or an entity snapshot, told apart by {@link #kind()}. Port of
 * {@code EventStoreEntryEnvelope}.
 */
public sealed interface EventStoreEntryEnvelope extends TypedEnvelope
    permits NonPersistedEventEnvelope, EventEnvelope, NonPersistedEntitySnapshotEnvelope, EntitySnapshotEnvelope {
  /**
   * @return {@code "event"} or {@code "snapshot"}
   */
  String kind();

  /**
   * @return the super kind
   */
  SuperKindType superKind();

  /**
   * @return id of the entity
   */
  UUID entityID();

  /**
   * @return the entity class name
   */
  String entityTypeName();

  /**
   * @return the event or entity: an instance when built by user code, a raw value when read from storage
   */
  Object value();
}
