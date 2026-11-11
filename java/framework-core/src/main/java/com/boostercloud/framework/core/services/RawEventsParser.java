package com.boostercloud.framework.core.services;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventStoreEntryEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Port of {@code services/raw-events-parser.ts}: keeps the non-deleted events of a page of event-store entries, groups
 * them by entity and calls the callback once per entity.
 *
 * <p>Execution model: every entity group runs on its own virtual thread (TypeScript starts one promise per group on
 * the event loop) and {@link #streamPerEntityEvents} returns only after every group has finished
 * ({@code Promise.allSettled}). The events of one entity are handed to a single callback call, in page order, so the
 * per-entity order is kept; different entities are processed in parallel. A failing callback is logged and does not
 * affect the other groups; this method never throws because of a callback.
 */
public final class RawEventsParser {

  /**
   * {@code EventsStreamingCallback}: processes the events of one entity. {@code provider.events()::produce} has this
   * shape.
   */
  @FunctionalInterface
  public interface EventsStreamingCallback {
    /**
     * @param entityName the entity class name
     * @param entityID the entity id
     * @param eventEnvelopes its events, in page order
     * @param config the configuration
     */
    void process(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes, BoosterConfig config);
  }

  /** Composite grouping key (TypeScript concatenates {@code `${entityTypeName}-${entityID}`}). */
  private record EntityKey(String entityTypeName, UUID entityID) {}

  private RawEventsParser() {}

  /**
   * {@code streamPerEntityEvents(config, eventEnvelopes, callbackFn)}.
   *
   * <ol>
   *   <li>keeps the entries of kind {@code event} (snapshots are dropped) that are not soft-deleted (an empty
   *       {@code deletedAt} counts as not deleted);</li>
   *   <li>groups them by entity type and id, groups in order of first appearance, events in page order;</li>
   *   <li>calls {@code callbackFn(entityTypeName, entityID, events, config)} for every group concurrently; a failure
   *       is logged as {@code An error occurred while processing events for entity <E> with ID <id>} with the
   *       error;</li>
   *   <li>returns when every group has finished.</li>
   * </ol>
   *
   * @param config the configuration (passed on to the callback)
   * @param eventEnvelopes a page of event-store entries
   * @param callbackFn the per-entity callback
   */
  public static void streamPerEntityEvents(
      BoosterConfig config, List<? extends EventStoreEntryEnvelope> eventEnvelopes, EventsStreamingCallback callbackFn) {
    Logger logger = Loggers.getLogger(config, "RawEventsParser#streamPerEntityEvents");
    Map<EntityKey, List<EventEnvelope>> eventEnvelopesPerEntity = new LinkedHashMap<>();
    for (EventStoreEntryEnvelope envelope : eventEnvelopes) {
      if (envelope instanceof EventEnvelope event && !event.isDeleted()) {
        eventEnvelopesPerEntity
            .computeIfAbsent(new EntityKey(event.entityTypeName(), event.entityID()), key -> new ArrayList<>())
            .add(event);
      }
    }

    List<Callable<Void>> processes = new ArrayList<>(eventEnvelopesPerEntity.size());
    for (Map.Entry<EntityKey, List<EventEnvelope>> group : eventEnvelopesPerEntity.entrySet()) {
      String entityTypeName = group.getKey().entityTypeName();
      UUID entityID = group.getKey().entityID();
      List<EventEnvelope> entityEnvelopes = group.getValue();
      processes.add(() -> {
        logger.debug("Streaming the following events for entity '" + entityTypeName + "' and ID '" + entityID + "':",
            entityEnvelopes);
        try {
          callbackFn.process(entityTypeName, entityID, entityEnvelopes, config);
        } catch (Throwable e) {
          logger.error("An error occurred while processing events for entity " + entityTypeName + " with ID " + entityID, e);
        }
        return null;
      });
    }
    // allSettled: a failing process must not stop the others, and we wait for all of them
    Promises.runAllSettled(processes);
  }
}
