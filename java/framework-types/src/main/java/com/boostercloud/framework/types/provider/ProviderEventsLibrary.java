package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The event store of a provider. Port of {@code ProviderEventsLibrary}.
 */
public interface ProviderEventsLibrary {
  /**
   * Converts the provider-native payload of an event-dispatch trigger into envelopes.
   *
   * @param rawEvents the provider-native payload
   * @return the events
   */
  List<EventEnvelope> rawToEnvelopes(Object rawEvents);

  /**
   * Converts a de-duplicated event stream ({@code EventStream = Array<unknown>}) into envelopes.
   *
   * @param config the configuration
   * @param context provider-native context
   * @param dedupEventStream the de-duplicated raw stream messages
   * @return the events
   */
  List<EventEnvelope> rawStreamToEnvelopes(BoosterConfig config, Object context, List<Object> dedupEventStream);

  /**
   * De-duplicates the messages of an event stream trigger.
   *
   * @param config the configuration
   * @param rawEvents the provider-native payload
   * @return the de-duplicated raw messages ({@code EventStream})
   */
  List<Object> dedupEventStream(BoosterConfig config, Object rawEvents);

  /**
   * Publishes envelopes to the event stream. {@code config} is the last parameter, as in TypeScript.
   *
   * @param entityName the entity class name
   * @param entityID the entity id
   * @param eventEnvelopes the events
   * @param config the configuration
   */
  void produce(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes, BoosterConfig config);

  /**
   * Events of an entity created after {@code since}.
   *
   * @param config the configuration
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @param since ISO timestamp, or {@code null} for all events
   * @return the events, in creation order
   */
  List<EventEnvelope> forEntitySince(BoosterConfig config, String entityTypeName, UUID entityID, String since);

  /**
   * {@link #forEntitySince(BoosterConfig, String, UUID, String)} without {@code since}.
   *
   * @param config the configuration
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @return all the events of the entity
   */
  default List<EventEnvelope> forEntitySince(BoosterConfig config, String entityTypeName, UUID entityID) {
    return forEntitySince(config, entityTypeName, entityID, null);
  }

  /**
   * @param config the configuration
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @return the latest snapshot, if any (TypeScript {@code undefined}/{@code null} both mean absent)
   */
  Optional<EntitySnapshotEnvelope> latestEntitySnapshot(BoosterConfig config, String entityTypeName, UUID entityID);

  /**
   * @param config the configuration
   * @param parameters what to search
   * @return the matching events
   */
  List<EventSearchResponse> search(BoosterConfig config, EventSearchParameters parameters);

  /**
   * A page of the distinct ids of an entity type.
   *
   * @param config the configuration
   * @param limit page size
   * @param afterCursor the cursor of the previous page, or {@code null}
   * @param entityTypeName the entity class name
   * @return the page
   */
  PaginatedEntitiesIdsResult searchEntitiesIDs(
      BoosterConfig config, int limit, Map<String, String> afterCursor, String entityTypeName);

  /**
   * Stores events. {@code config} is the last parameter.
   *
   * @param eventEnvelopes the events to store
   * @param config the configuration
   * @return the stored events (with {@code createdAt}, possibly {@code id})
   */
  List<EventEnvelope> store(List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config);

  /**
   * Stores a snapshot. {@code config} is the last parameter.
   *
   * @param snapshotEnvelope the snapshot
   * @param config the configuration
   * @return the stored snapshot (with {@code createdAt} and {@code persistedAt})
   */
  EntitySnapshotEnvelope storeSnapshot(NonPersistedEntitySnapshotEnvelope snapshotEnvelope, BoosterConfig config);

  /**
   * Records that an event was dispatched (idempotency guard). {@code config} is the last parameter.
   *
   * @param eventEnvelope the dispatched event
   * @param config the configuration
   * @return {@code true} if it was stored, {@code false} if it was already there; throws on any other failure
   */
  boolean storeDispatched(EventEnvelope eventEnvelope, BoosterConfig config);

  /**
   * @param config the configuration
   * @param parameters the event identification
   * @return the matching events, with their database {@code id}
   */
  List<EventEnvelope> findDeletableEvent(BoosterConfig config, EventDeleteParameters parameters);

  /**
   * @param config the configuration
   * @param parameters the snapshot identification
   * @return the matching snapshots, with their database {@code id}
   */
  List<EntitySnapshotEnvelope> findDeletableSnapshot(BoosterConfig config, SnapshotDeleteParameters parameters);

  /**
   * Soft-deletes events (providers set {@code deletedAt}).
   *
   * @param config the configuration
   * @param events events returned by {@link #findDeletableEvent}
   */
  void deleteEvent(BoosterConfig config, List<EventEnvelope> events);

  /**
   * Deletes snapshots.
   *
   * @param config the configuration
   * @param snapshots snapshots returned by {@link #findDeletableSnapshot}
   */
  void deleteSnapshot(BoosterConfig config, List<EntitySnapshotEnvelope> snapshots);
}
