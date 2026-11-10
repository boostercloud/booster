package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deleting events (experimental Booster feature): events are soft-deleted (their {@code value} is emptied and
 * {@code deletedAt} set), snapshots are removed. Port of {@code library/event-delete-adapter.ts}.
 */
public final class EventDeleteAdapter {

  private EventDeleteAdapter() {}

  /**
   * {@code findDeletableEvent(eventRegistry, config, parameters)}: the non-deleted events of the entity created exactly
   * at {@code parameters.createdAt}.
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param parameters the event identification
   * @return the events, with their database id as {@code id}
   */
  public static List<EventEnvelope> findDeletableEvent(EventRegistry eventRegistry, BoosterConfig config,
      EventDeleteParameters parameters) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#findDeletableEvent");
    String stringifyParameters = BoosterJson.toJson(parameters);
    logger.debug("Initiating a deletable event search for " + stringifyParameters);
    Map<String, Object> filter =
        buildDeleteEventFilter(parameters.entityTypeName(), parameters.entityID(), parameters.createdAt());
    List<EventEnvelope> result = new ArrayList<>();
    for (Map<String, Object> event : eventRegistry.query(filter)) {
      result.add(Envelopes.toEventEnvelope(event).toBuilder().id(String.valueOf(event.get("_id"))).build());
    }
    logger.debug("Finished deletable event search for " + stringifyParameters);
    return result;
  }

  /**
   * {@code findDeletableSnapshot(eventRegistry, config, parameters)}: the snapshots of the entity whose
   * {@code createdAt} (= {@code snapshottedEventCreatedAt}) is exactly {@code parameters.createdAt}.
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param parameters the snapshot identification
   * @return the snapshots, with their database id as {@code id}
   */
  public static List<EntitySnapshotEnvelope> findDeletableSnapshot(EventRegistry eventRegistry, BoosterConfig config,
      SnapshotDeleteParameters parameters) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#findDeletableSnapshot");
    String stringifyParameters = BoosterJson.toJson(parameters);
    logger.debug("Initiating a deletable snapshot search for " + stringifyParameters);
    Map<String, Object> filter =
        buildDeleteEntityFilter(parameters.entityTypeName(), parameters.entityID(), parameters.createdAt());
    List<EntitySnapshotEnvelope> result = new ArrayList<>();
    for (Map<String, Object> snapshot : eventRegistry.query(filter)) {
      result.add(Envelopes.toSnapshotEnvelope(snapshot).toBuilder().id(String.valueOf(snapshot.get("_id"))).build());
    }
    logger.debug("Finished deletable snapshot search for " + stringifyParameters);
    return result;
  }

  /**
   * {@code deleteEvent(eventRegistry, config, events)}: replaces each event, sequentially, by a copy with
   * {@code deletedAt = now} and {@code value = {}} (the stored document keeps every other field, plus {@code id}).
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param events events returned by {@link #findDeletableEvent}
   */
  public static void deleteEvent(EventRegistry eventRegistry, BoosterConfig config, List<EventEnvelope> events) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#deleteEvent");
    String stringifyParameters = BoosterJson.toJson(events);
    logger.debug("Initiating an event delete for " + stringifyParameters);
    if (events == null || events.isEmpty()) {
      logger.warn("Could not find events to delete");
      return;
    }
    for (EventEnvelope event : events) {
      EventEnvelope newEvent = buildNewEvent(event);
      eventRegistry.replaceOrDeleteItem(event.id(), Envelopes.toDocument(newEvent));
    }
    logger.debug("Finished event delete for " + stringifyParameters);
  }

  /**
   * {@code deleteSnapshot(eventRegistry, config, snapshots)}: removes each snapshot, sequentially.
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param snapshots snapshots returned by {@link #findDeletableSnapshot}
   */
  public static void deleteSnapshot(EventRegistry eventRegistry, BoosterConfig config,
      List<EntitySnapshotEnvelope> snapshots) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#deleteSnapshot");
    String stringifyParameters = BoosterJson.toJson(snapshots);
    logger.debug("Initiating a snapshot delete for " + stringifyParameters);
    if (snapshots == null || snapshots.isEmpty()) {
      logger.warn("Could not find snapshot to delete");
      return;
    }
    for (EntitySnapshotEnvelope snapshot : snapshots) {
      eventRegistry.replaceOrDeleteItem(snapshot.id());
    }
    logger.debug("Finished snapshot delete for " + stringifyParameters);
  }

  private static EventEnvelope buildNewEvent(EventEnvelope existingEvent) {
    return existingEvent.toBuilder().deletedAt(Dates.nowIso()).value(new LinkedHashMap<String, Object>()).build();
  }

  private static Map<String, Object> buildDeleteEventFilter(String entityTypeName, UUID entityId, String createdAt) {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("entityID", entityId == null ? null : entityId.toString());
    filter.put("entityTypeName", entityTypeName);
    filter.put("createdAt", createdAt);
    filter.put("kind", "event");
    filter.put("deletedAt", Map.of("$exists", false));
    return filter;
  }

  private static Map<String, Object> buildDeleteEntityFilter(String entityTypeName, UUID entityId, String createdAt) {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("entityTypeName", entityTypeName);
    filter.put("entityID", entityId == null ? null : entityId.toString());
    filter.put("kind", "snapshot");
    filter.put("createdAt", createdAt);
    filter.put("deletedAt", Map.of("$exists", false));
    return filter;
  }
}
