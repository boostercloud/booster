package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.RawEventsParser;
import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.util.Arrays;
import java.util.List;

/**
 * Port of {@code booster-event-dispatcher.ts}: the entry point of a page of raw events coming from the provider's
 * event store change feed ({@code UserApp.boosterEventDispatcher}). The raw events are converted with
 * {@code provider.events().rawToEnvelopes(rawEvents)} and processed per entity
 * ({@link RawEventsParser#streamPerEntityEvents} with {@link BoosterEventProcessor#eventProcessor}). It returns when
 * every entity has been processed and <b>never throws</b>: failures are logged
 * ({@code Unhandled error while dispatching event: }). Trace point: {@code DISPATCH_EVENTS}.
 */
public final class BoosterEventDispatcher {

  private BoosterEventDispatcher() {}

  /**
   * {@code dispatch(rawEvents, config)}.
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   */
  public static void dispatch(Object rawEvents, BoosterConfig config) {
    Tracer.trace(TraceActionTypes.DISPATCH_EVENTS.value(), BoosterEventDispatcher.class, "dispatch",
        Arrays.asList(rawEvents, config), () -> {
          dispatch(rawEvents, config, new EventStore(config), new ReadModelStore(config));
          return null;
        });
  }

  /**
   * {@link #dispatch(Object, BoosterConfig)} with the given stores (for tests).
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   * @param eventStore the event store
   * @param readModelStore the read model store
   */
  static void dispatch(Object rawEvents, BoosterConfig config, EventStore eventStore, ReadModelStore readModelStore) {
    Logger logger = Loggers.getLogger(config, "BoosterEventDispatcher#dispatch");
    // The sink renders the raw events (at full depth) only when debug is enabled
    logger.debug("Event workflow started for raw events:", rawEvents);
    try {
      List<EventEnvelope> eventEnvelopes = config.provider().events().rawToEnvelopes(rawEvents);
      RawEventsParser.streamPerEntityEvents(config, eventEnvelopes,
          BoosterEventProcessor.eventProcessor(eventStore, readModelStore));
    } catch (Throwable e) {
      logger.error("Unhandled error while dispatching event: ", e);
    }
  }
}
