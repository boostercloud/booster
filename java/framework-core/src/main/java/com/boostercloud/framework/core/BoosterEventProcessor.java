package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.RawEventsParser.EventsStreamingCallback;
import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventHandlerInterface;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.errors.EventHandlerGlobalError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Port of {@code booster-event-processor.ts}: the per-entity callback of the event pipeline
 * ({@link #eventProcessor(EventStore, ReadModelStore)}).
 *
 * <p>For the events of one entity:
 *
 * <ol>
 *   <li><b>dedup</b>: {@code EventStore.storeDispatchedEvent} for every event (concurrently); the events the store
 *       already knew are dropped with the warning {@code Event has already been dispatched. Skipping.};</li>
 *   <li>if no event is left, nothing else happens;</li>
 *   <li>otherwise, concurrently: the <b>event handlers</b> of the remaining events, and — unless the entity name is a
 *       notification topic ({@code config.topicToEvent}) — the <b>snapshot</b> of the entity
 *       ({@code EventStore.fetchEntitySnapshot}) followed by the <b>read model projection</b>
 *       ({@code ReadModelStore.project}). Both are awaited; a failure of either is rethrown as a {@link PromisesError}
 *       after both finished (the caller, {@code RawEventsParser}, logs it).</li>
 * </ol>
 *
 * <p>Event handlers: for each event (concurrently), the handlers registered for its type followed by the global
 * handlers ({@code @GlobalEventHandler}) run concurrently with one shared event instance, bound from the
 * <b>un-migrated</b> envelope value (only reducers see schema-migrated events). Each handler gets a fresh
 * {@link Register} (the event's request id and user, empty response headers) whose events are stored after the
 * handler returns. A failing handler goes to the global error handler ({@code onDispatchEventHandlerError}); the
 * failures that remain are logged after the other handlers finished. Event handlers never make this step fail.
 *
 * <p>Execution model: the concurrent steps run on virtual threads ({@link Promises}) and are always joined before
 * returning, never failing fast. Unlike TypeScript, which interleaves them on one thread, handlers of the same batch
 * (and the projection) run in parallel: code shared between them must be thread-safe, and handlers should not mutate
 * the shared event instance (records are recommended). Reductions of one entity stay sequential.
 *
 * <p>The steps are package-private instance methods of a stateless instance (the TypeScript class has private static
 * methods that its tests replace); the public API is the static {@link #eventProcessor}. Several logger locations say
 * {@code BoosterEventDispatcher}, as in TypeScript. Trace point: {@code EVENT_HANDLERS_PROCESS}.
 */
public final class BoosterEventProcessor {

  private static final BoosterEventProcessor INSTANCE = new BoosterEventProcessor();

  BoosterEventProcessor() {}

  /**
   * {@code eventProcessor(eventStore, readModelStore)}: the callback that {@code RawEventsParser.streamPerEntityEvents}
   * calls once per entity of a page of events.
   *
   * @param eventStore the event store (dedup and snapshots)
   * @param readModelStore the read model store (projections)
   * @return the callback
   */
  public static EventsStreamingCallback eventProcessor(EventStore eventStore, ReadModelStore readModelStore) {
    return INSTANCE.callback(eventStore, readModelStore);
  }

  EventsStreamingCallback callback(EventStore eventStore, ReadModelStore readModelStore) {
    return (entityName, entityID, eventEnvelopes, config) -> {
      // Filter events that have already been dispatched
      List<EventEnvelope> eventsNotDispatched = filterDispatched(config, eventEnvelopes, eventStore);
      List<Callable<Void>> eventEnvelopesProcessors = new ArrayList<>();

      // Only process events that haven't been dispatched yet
      if (!eventsNotDispatched.isEmpty()) {
        eventEnvelopesProcessors.add(() -> {
          dispatchEntityEventsToEventHandlers(eventsNotDispatched, config);
          return null;
        });
        // Read models are not updated for notification events (events that are not related to an entity but a topic)
        if (!config.topicToEvent.containsKey(entityName)) {
          eventEnvelopesProcessors.add(() -> {
            snapshotAndUpdateReadModels(config, entityName, entityID, eventStore, readModelStore);
            return null;
          });
        }
      }

      Promises.runAllSettledAndFulfilled(eventEnvelopesProcessors);
    };
  }

  /**
   * {@code filterDispatched(config, eventEnvelopes, eventStore)}.
   *
   * @param config the configuration
   * @param eventEnvelopes the events of one entity
   * @param eventStore the event store
   * @return the events that had not been dispatched yet, in order
   */
  List<EventEnvelope> filterDispatched(BoosterConfig config, List<EventEnvelope> eventEnvelopes, EventStore eventStore) {
    Logger logger = Loggers.getLogger(config, "BoosterEventDispatcher#filterDispatched");
    List<Callable<Boolean>> marks = new ArrayList<>(eventEnvelopes.size());
    for (EventEnvelope eventEnvelope : eventEnvelopes) {
      marks.add(() -> {
        boolean result = eventStore.storeDispatchedEvent(eventEnvelope);
        if (!result) {
          logger.warn("Event has already been dispatched. Skipping.", eventEnvelope);
        }
        return result;
      });
    }
    List<Boolean> filteredResults = Promises.runAll(marks);
    List<EventEnvelope> eventsNotDispatched = new ArrayList<>();
    for (int index = 0; index < eventEnvelopes.size(); index++) {
      if (Boolean.TRUE.equals(filteredResults.get(index))) {
        eventsNotDispatched.add(eventEnvelopes.get(index));
      }
    }
    return eventsNotDispatched;
  }

  /**
   * {@code snapshotAndUpdateReadModels(config, entityName, entityID, eventStore, readModelStore)}: a failure while
   * fetching or reducing the snapshot is logged and the projection skipped; projection failures propagate.
   *
   * @param config the configuration
   * @param entityName the entity class name
   * @param entityID the entity id
   * @param eventStore the event store
   * @param readModelStore the read model store
   */
  void snapshotAndUpdateReadModels(BoosterConfig config, String entityName, UUID entityID, EventStore eventStore,
      ReadModelStore readModelStore) {
    Logger logger = Loggers.getLogger(config, "BoosterEventDispatcher#snapshotAndUpdateReadModels");
    EntitySnapshotEnvelope entitySnapshot = null;
    try {
      entitySnapshot = eventStore.fetchEntitySnapshot(entityName, entityID).orElse(null);
    } catch (Throwable e) {
      logger.error("Error while fetching or reducing entity snapshot:", e);
    }
    if (entitySnapshot == null) {
      logger.debug("No new snapshot generated, skipping read models projection");
      return;
    }
    logger.debug("Snapshot loaded and started read models projection:", entitySnapshot);
    readModelStore.project(entitySnapshot);
  }

  /**
   * {@code dispatchEntityEventsToEventHandlers(entityEventEnvelopes, config)}: runs the handlers of every event (see
   * the class documentation). Never throws because of a handler.
   *
   * @param entityEventEnvelopes the events
   * @param config the configuration
   */
  void dispatchEntityEventsToEventHandlers(List<EventEnvelope> entityEventEnvelopes, BoosterConfig config) {
    Tracer.trace(TraceActionTypes.EVENT_HANDLERS_PROCESS.value(), BoosterEventProcessor.class,
        "dispatchEntityEventsToEventHandlers", Arrays.asList(entityEventEnvelopes, config), () -> {
          doDispatchEntityEventsToEventHandlers(entityEventEnvelopes, config);
          return null;
        });
  }

  private void doDispatchEntityEventsToEventHandlers(List<EventEnvelope> entityEventEnvelopes, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "BoosterEventDispatcher.dispatchEntityEventsToEventHandlers");
    try {
      Promises.forEachAllSettledAndFulfilled(entityEventEnvelopes, eventEnvelope -> {
        List<Class<?>> eventHandlers = new ArrayList<>();
        List<Class<?>> specificEventHandlers = config.eventHandlers.get(eventEnvelope.typeName());
        if (specificEventHandlers != null) {
          eventHandlers.addAll(specificEventHandlers);
        }
        List<Class<?>> globalEventHandlers = config.eventHandlers.get(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS);
        if (globalEventHandlers != null && !globalEventHandlers.isEmpty()) {
          eventHandlers.addAll(globalEventHandlers);
        }
        if (eventHandlers.isEmpty()) {
          logger.debug("No event-handlers found for event " + eventEnvelope.typeName() + ". Skipping...");
          return;
        }
        Object eventInstance = getEventInstance(config, eventEnvelope);
        try {
          Promises.forEachAllSettledAndFulfilled(eventHandlers, eventHandler -> {
            logger.debug("Calling \"handle\" method on event handler: ", eventHandler);
            callEventHandler(eventHandler, eventInstance, eventEnvelope, config);
          });
        } catch (PromisesError error) {
          logger.error("Failed to process handlers for event " + eventEnvelope.typeName() + ":", error.failedReasons());
        } catch (Throwable error) {
          logger.error("Unexpected error processing handlers for event " + eventEnvelope.typeName() + ":", error);
        }
      });
    } catch (PromisesError error) {
      logger.error("Failed to process events:", error.failedReasons());
    } catch (Throwable error) {
      logger.error("Unexpected error processing events:", error);
    }
  }

  /**
   * {@code getEventInstance(config, eventEnvelope)}: the envelope value bound to the class registered in
   * {@code config.events}, or else in {@code config.notifications}.
   *
   * @param config the configuration
   * @param eventEnvelope the event
   * @return the event instance
   * @throws IllegalStateException when the type is registered as neither (TypeScript: a {@code TypeError})
   */
  Object getEventInstance(BoosterConfig config, EventEnvelope eventEnvelope) {
    EventMetadata eventMetadata = config.events.get(eventEnvelope.typeName());
    Class<?> eventClass = eventMetadata != null ? eventMetadata.type() : null;
    if (eventClass == null) {
      NotificationMetadata notificationMetadata = config.notifications.get(eventEnvelope.typeName());
      eventClass = notificationMetadata != null ? notificationMetadata.type() : null;
    }
    if (eventClass == null) {
      throw new IllegalStateException("No @Event or @Notification class is registered for \"" + eventEnvelope.typeName()
          + "\": cannot create the instance passed to its event handlers");
    }
    return Instances.createInstance(eventClass, eventEnvelope.value());
  }

  /**
   * {@code callEventHandler(eventHandler, eventInstance, eventEnvelope, config)}: runs the static
   * {@code handle(event, register)} of one handler class with a fresh register, routes its failure through the
   * global error handler and stores the events it registered (also after a swallowed failure).
   *
   * @param eventHandler the handler class
   * @param eventInstance the event instance
   * @param eventEnvelope the event
   * @param config the configuration
   */
  void callEventHandler(Class<?> eventHandler, Object eventInstance, EventEnvelope eventEnvelope, BoosterConfig config) {
    Register register = new Register(eventEnvelope.requestID(), new LinkedHashMap<>(), RegisterHandler::flush,
        eventEnvelope.currentUser());
    try {
      Logger logger = Loggers.getLogger(config, "BoosterEventProcessor#handleEvent");
      logger.debug("Calling \"handle\" method on event handler: ", eventHandler);
      UserCode.invokeStatic(eventHandler, EventHandlerInterface.HANDLE_METHOD, eventInstance, register);
    } catch (Exception e) {
      // TypeScript reads `e.eventHandlerMetadata`, which nothing sets: the metadata is always undefined
      Throwable error = new BoosterGlobalErrorDispatcher(config)
          .dispatch(new EventHandlerGlobalError(eventEnvelope, eventInstance, null, e));
      if (error != null) {
        throw UserCode.rethrow(error);
      }
    }
    handleRegister(config, register);
  }

  /**
   * {@code RegisterHandler.handle(config, register)} after a handler (separate so that tests can observe it).
   *
   * @param config the configuration
   * @param register the handler's register
   */
  void handleRegister(BoosterConfig config, Register register) {
    RegisterHandler.handle(config, register);
  }
}
