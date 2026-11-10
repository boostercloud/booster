package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.services.RawEventsParser;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import java.util.Arrays;
import java.util.List;

/**
 * Port of {@code booster-event-stream-producer.ts}: forwards events from the event store change feed to the event
 * stream ({@code UserApp.boosterProduceEventStream}). The request is converted with
 * {@code provider.events().rawToEnvelopes(request)} and {@code provider.events().produce(entityName, entityID,
 * envelopes, config)} is called once per entity through {@link RawEventsParser#streamPerEntityEvents} (snapshots and
 * soft-deleted events are dropped; per-entity failures are logged there). It <b>never throws</b>: other failures are
 * logged ({@code Unhandled error while producing events: }). Trace point: {@code PRODUCE_STREAM_EVENTS}.
 */
public final class BoosterEventStreamProducer {

  private BoosterEventStreamProducer() {}

  /**
   * {@code produce(request, config)}.
   *
   * @param request the provider-specific request
   * @param config the configuration
   */
  public static void produce(Object request, BoosterConfig config) {
    Tracer.trace(TraceActionTypes.PRODUCE_STREAM_EVENTS.value(), BoosterEventStreamProducer.class, "produce",
        Arrays.asList(request, config), () -> {
          doProduce(request, config);
          return null;
        });
  }

  private static void doProduce(Object request, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "BoosterEventStreamProducer#produce");
    logger.debug("Produce event workflow started for request:", request);
    try {
      ProviderEventsLibrary events = config.provider().events();
      List<EventEnvelope> eventEnvelopes = events.rawToEnvelopes(request);
      RawEventsParser.streamPerEntityEvents(config, eventEnvelopes, events::produce);
    } catch (Throwable e) {
      logger.error("Unhandled error while producing events: ", e);
    }
  }
}
