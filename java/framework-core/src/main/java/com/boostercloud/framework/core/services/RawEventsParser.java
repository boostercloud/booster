package com.boostercloud.framework.core.services;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.List;

/**
 * Port of {@code services/raw-events-parser.ts}: keeps the non-deleted {@code kind == "event"} envelopes of a page,
 * groups them by entity and calls the callback once per entity, concurrently, isolating failures.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.2).
 */
public final class RawEventsParser {

  /**
   * {@code EventsStreamingCallback}: processes the events of one entity.
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

  private RawEventsParser() {}

  /**
   * {@code streamPerEntityEvents(config, eventEnvelopes, callbackFn)}. Implemented by stage core-2a.
   *
   * @param config the configuration
   * @param eventEnvelopes a page of envelopes
   * @param callbackFn the per-entity callback
   */
  public static void streamPerEntityEvents(
      BoosterConfig config, List<EventEnvelope> eventEnvelopes, EventsStreamingCallback callbackFn) {
    throw new UnsupportedOperationException("not implemented yet: RawEventsParser#streamPerEntityEvents");
  }
}
