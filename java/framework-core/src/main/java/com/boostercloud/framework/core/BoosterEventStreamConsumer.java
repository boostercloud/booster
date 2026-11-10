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
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import java.util.Arrays;
import java.util.List;

/**
 * Port of {@code booster-event-stream-consumer.ts}: the entry point of events arriving through the event stream
 * ({@code UserApp.boosterConsumeEventStream}). The provider de-duplicates the stream messages
 * ({@code dedupEventStream(config, rawEvents)}), converts them ({@code rawStreamToEnvelopes(config, rawEvents,
 * dedupEvents)}) and the envelopes go through the same per-entity pipeline as {@link BoosterEventDispatcher}
 * (including the dispatched-events dedup). It <b>never throws</b>: failures are logged
 * ({@code Unhandled error while consuming event: }). Trace point: {@code CONSUME_STREAM_EVENTS}.
 */
public final class BoosterEventStreamConsumer {

  private BoosterEventStreamConsumer() {}

  /**
   * {@code consume(rawEvents, config)}.
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   */
  public static void consume(Object rawEvents, BoosterConfig config) {
    Tracer.trace(TraceActionTypes.CONSUME_STREAM_EVENTS.value(), BoosterEventStreamConsumer.class, "consume",
        Arrays.asList(rawEvents, config), () -> {
          consume(rawEvents, config, new EventStore(config), new ReadModelStore(config));
          return null;
        });
  }

  /**
   * {@link #consume(Object, BoosterConfig)} with the given stores (for tests).
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   * @param eventStore the event store
   * @param readModelStore the read model store
   */
  static void consume(Object rawEvents, BoosterConfig config, EventStore eventStore, ReadModelStore readModelStore) {
    Logger logger = Loggers.getLogger(config, "BoosterEventStreamConsumer#consume");
    logger.debug("Stream event workflow started for raw events:", rawEvents);
    try {
      ProviderEventsLibrary events = config.provider().events();
      List<Object> dedupEvents = events.dedupEventStream(config, rawEvents);
      List<EventEnvelope> eventEnvelopes = events.rawStreamToEnvelopes(config, rawEvents, dedupEvents);
      RawEventsParser.streamPerEntityEvents(config, eventEnvelopes,
          BoosterEventProcessor.eventProcessor(eventStore, readModelStore));
    } catch (Throwable e) {
      logger.error("Unhandled error while consuming event: ", e);
    }
  }
}
