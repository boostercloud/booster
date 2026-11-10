package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.RawEventsParser.EventsStreamingCallback;
import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.EventHandlerGlobalError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-event-processor.test.ts}, plus the paths the TypeScript tests leave uncovered. */
class BoosterEventProcessorTest extends BoosterTestSupport {

  record Handled(Class<?> handler, Object event, Register register) {
    Object handlerClass() {
      return handler;
    }
  }

  static final List<Handled> HANDLED = Collections.synchronizedList(new ArrayList<>());
  static final AtomicInteger GET_PREFIXED_ID_CALLS = new AtomicInteger();
  static final List<Object> GLOBAL_ERRORS = Collections.synchronizedList(new ArrayList<>());
  static volatile boolean swallowHandlerErrors;

  public record SomeEvent(UUID id) {
    public UUID entityID() {
      return id;
    }

    public String getPrefixedId(String prefix) {
      GET_PREFIXED_ID_CALLS.incrementAndGet();
      return prefix + "-" + id;
    }
  }

  public record SomeNotification() {}

  public record SomeEntity(UUID id) {}

  public static final class FakeHandler1 {
    public static void handle(Object event, Register register) {
      HANDLED.add(new Handled(FakeHandler1.class, event, register));
    }
  }

  public static final class FakeHandler2 {
    public static void handle(Object event, Register register) {
      HANDLED.add(new Handled(FakeHandler2.class, event, register));
    }
  }

  public static final class FakeGlobalHandler {
    public static void handle(Object event, Register register) {
      HANDLED.add(new Handled(FakeGlobalHandler.class, event, register));
    }
  }

  public static final class FailingHandler {
    public static void handle(Object event, Register register) {
      HANDLED.add(new Handled(FailingHandler.class, event, register));
      register.events(new SomeEvent(UUID.of("registered before failing")));
      throw new IllegalStateException("Handler failed");
    }
  }

  public static final class PromisesErrorHandler {
    public static void handle(Object event, Register register) {
      HANDLED.add(new Handled(PromisesErrorHandler.class, event, register));
      throw new PromisesError(List.of(new IllegalStateException("Handler 1 failed"),
          new IllegalStateException("Handler 2 failed")));
    }
  }

  public static final class SlowHandler {
    public static void handle(Object event, Register register) throws InterruptedException {
      Thread.sleep(100);
      register.events(event);
      HANDLED.add(new Handled(SlowHandler.class, event, register));
    }
  }

  /** An actual handler whose {@code handle} calls an instance method of the event. */
  public static final class AnEventHandler {
    public static void handle(SomeEvent event, Register register) {
      event.getPrefixedId("prefix");
    }
  }

  public static final class WithoutHandleMethod {}

  public static final class EventHandlerErrors {
    public static Throwable onDispatchEventHandlerError(Throwable error, Object eventEnvelope, Object eventHandlerMetadata,
        Object eventInstance) {
      GLOBAL_ERRORS.add(new EventHandlerGlobalError(eventEnvelope, eventInstance, eventHandlerMetadata, error));
      return swallowHandlerErrors ? null : new IllegalArgumentException("handled: " + error.getMessage());
    }

    public static Throwable onError(Throwable error) {
      return error;
    }
  }

  private static final UserEnvelope USER = UserEnvelope.builder().username("user@example.com").roles(List.of("Admin")).build();

  private static final EventEnvelope SOME_EVENT = EventEnvelope.builder()
      .version(1)
      .superKind(SuperKindType.DOMAIN)
      .entityID(UUID.of("42"))
      .entityTypeName("SomeEntity")
      .value(Map.of("id", "42"))
      .requestID(UUID.of("123"))
      .currentUser(USER)
      .typeName("SomeEvent")
      .createdAt("2026-11-10T10:00:00.000Z")
      .build();

  private static final EventEnvelope SOME_NOTIFICATION = EventEnvelope.builder()
      .version(1)
      .superKind(SuperKindType.DOMAIN)
      .entityID(UUID.of("default"))
      .entityTypeName("defaultTopic")
      .value(Map.of())
      .requestID(UUID.of("123"))
      .typeName("SomeNotification")
      .createdAt("2026-11-10T10:00:00.000Z")
      .build();

  private static final EntitySnapshotEnvelope SOME_ENTITY_SNAPSHOT = EntitySnapshotEnvelope.builder()
      .version(1)
      .superKind(SuperKindType.DOMAIN)
      .entityID(UUID.of("42"))
      .entityTypeName("SomeEntity")
      .value(Map.of("id", "42"))
      .requestID(UUID.of("234"))
      .typeName("SomeEntity")
      .createdAt("an uncertain future")
      .persistedAt("a few nanoseconds later")
      .snapshottedEventCreatedAt("an uncertain future")
      .build();

  private static final String HANDLERS_PREFIX = "[Booster]|BoosterEventDispatcher.dispatchEntityEventsToEventHandlers: ";

  private BoosterConfig config;
  private Logger logger;
  private BoosterEventProcessor processor;

  @BeforeEach
  void setUp() {
    HANDLED.clear();
    GLOBAL_ERRORS.clear();
    GET_PREFIXED_ID_CALLS.set(0);
    swallowHandlerErrors = false;
    config = new BoosterConfig("test");
    config.events.put("SomeEvent", new EventMetadata(SomeEvent.class));
    config.notifications.put("SomeNotification", new NotificationMetadata(SomeNotification.class));
    config.reducers.put("SomeEvent", new ReducerMetadata(SomeEntity.class, "reduce"));
    config.eventStoreRetry.initialDelay = 1;
    logger = mock(Logger.class);
    config.logger = logger;
    processor = spy(new BoosterEventProcessor());
    // TypeScript replaces RegisterHandler.handle with a fake in these tests
    doNothing().when(processor).handleRegister(any(), any());
  }

  private static Object instanceOfSomeEvent() {
    return new SomeEvent(UUID.of("42"));
  }

  @Nested
  class EventProcessor {

    private EventStore eventStore;
    private ReadModelStore readModelStore;

    @BeforeEach
    void stubSteps() {
      eventStore = mock(EventStore.class);
      readModelStore = mock(ReadModelStore.class);
      doNothing().when(processor).snapshotAndUpdateReadModels(any(), any(), any(), any(), any());
      doNothing().when(processor).dispatchEntityEventsToEventHandlers(any(), any());
    }

    @Test
    void waitsForTheSnapshotGenerationAndReadModelUpdate() {
      doReturn(List.of(SOME_EVENT)).when(processor).filterDispatched(any(), any(), any());
      EventsStreamingCallback callback = processor.callback(eventStore, readModelStore);

      callback.process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config);

      verify(processor, times(1)).snapshotAndUpdateReadModels(config, "SomeEntity", UUID.of("42"), eventStore,
          readModelStore);
    }

    @Test
    void waitsForTheEventToBeHandledByTheEventHandlers() {
      doReturn(List.of(SOME_EVENT)).when(processor).filterDispatched(any(), any(), any());
      EventsStreamingCallback callback = processor.callback(eventStore, readModelStore);

      callback.process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config);

      verify(processor, times(1)).dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);
      verify(processor).filterDispatched(config, List.of(SOME_EVENT), eventStore);
    }

    @Test
    void doesNotUpdateTheReadModelsWhenTheEntityNameIsANotificationTopic() {
      doReturn(List.of(SOME_EVENT)).when(processor).filterDispatched(any(), any(), any());
      config.topicToEvent.put("SomeEntity", "SomeEvent");
      EventsStreamingCallback callback = processor.callback(eventStore, readModelStore);

      callback.process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config);

      verify(processor, never()).snapshotAndUpdateReadModels(any(), any(), any(), any(), any());
      verify(processor).dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);
    }

    @Test
    void doesNothingElseWhenEveryEventWasAlreadyDispatched() {
      doReturn(List.of()).when(processor).filterDispatched(any(), any(), any());
      EventsStreamingCallback callback = processor.callback(eventStore, readModelStore);

      callback.process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config);

      verify(processor, never()).snapshotAndUpdateReadModels(any(), any(), any(), any(), any());
      verify(processor, never()).dispatchEntityEventsToEventHandlers(any(), any());
    }

    @Test
    void runsTheHandlersAndTheProjectionConcurrentlyAndFailsAfterBothFinished() {
      doReturn(List.of(SOME_EVENT)).when(processor).filterDispatched(any(), any(), any());
      IllegalStateException projectionError = new IllegalStateException("projection failed");
      List<String> handlersDone = Collections.synchronizedList(new ArrayList<>());
      doThrow(projectionError).when(processor).snapshotAndUpdateReadModels(any(), any(), any(), any(), any());
      doAnswer(invocation -> {
        Thread.sleep(100);
        handlersDone.add("done");
        return null;
      }).when(processor).dispatchEntityEventsToEventHandlers(any(), any());
      EventsStreamingCallback callback = processor.callback(eventStore, readModelStore);

      assertThatThrownBy(() -> callback.process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config))
          .isInstanceOfSatisfying(PromisesError.class,
              error -> assertThat(error.failedReasons()).containsExactly(projectionError));
      assertThat(handlersDone).containsExactly("done");
    }

    @Test
    void thePublicCallbackDedupsHandlesAndProjects() {
      // the static form uses a shared stateless instance, not the spy
      config.eventHandlers.put("SomeEvent", List.of(FakeHandler1.class));
      when(eventStore.storeDispatchedEvent(SOME_EVENT)).thenReturn(true);
      when(eventStore.fetchEntitySnapshot("SomeEntity", UUID.of("42"))).thenReturn(Optional.of(SOME_ENTITY_SNAPSHOT));

      BoosterEventProcessor.eventProcessor(eventStore, readModelStore)
          .process("SomeEntity", UUID.of("42"), List.of(SOME_EVENT), config);

      assertThat(HANDLED).extracting(Handled::handlerClass).containsExactly(FakeHandler1.class);
      verify(readModelStore).project(SOME_ENTITY_SNAPSHOT);
    }
  }

  @Nested
  class SnapshotAndUpdateReadModels {

    @Test
    void getsTheUpdatedStateOfTheEventEntity() {
      EventStore eventStore = mock(EventStore.class);
      ReadModelStore readModelStore = mock(ReadModelStore.class);
      when(eventStore.fetchEntitySnapshot(any(), any())).thenReturn(Optional.empty());

      processor.snapshotAndUpdateReadModels(config, "SomeEntity", UUID.of("42"), eventStore, readModelStore);

      verify(eventStore, times(1)).fetchEntitySnapshot("SomeEntity", UUID.of("42"));
      verifyNoInteractions(readModelStore);
      verify(logger).debug("[Booster]|BoosterEventDispatcher#snapshotAndUpdateReadModels: ",
          "No new snapshot generated, skipping read models projection");
    }

    @Test
    void projectsTheEntityStateToTheCorrespondingReadModels() {
      EventStore eventStore = mock(EventStore.class);
      ReadModelStore readModelStore = mock(ReadModelStore.class);
      when(eventStore.fetchEntitySnapshot(any(), any())).thenReturn(Optional.of(SOME_ENTITY_SNAPSHOT));

      processor.snapshotAndUpdateReadModels(config, "SomeEntity", UUID.of("42"), eventStore, readModelStore);

      verify(readModelStore, times(1)).project(SOME_ENTITY_SNAPSHOT);
    }

    @Test
    void logsAndDoesNotThrowAReductionErrorAndDoesNotProject() {
      EventStore eventStore = mock(EventStore.class);
      ReadModelStore readModelStore = mock(ReadModelStore.class);
      IllegalStateException error = new IllegalStateException("some error");
      when(eventStore.fetchEntitySnapshot(any(), any())).thenThrow(error);

      assertThatCode(() -> processor.snapshotAndUpdateReadModels(config, "SomeEntity", UUID.of("42"), eventStore,
          readModelStore)).doesNotThrowAnyException();

      verifyNoInteractions(readModelStore);
      verify(logger).error("[Booster]|BoosterEventDispatcher#snapshotAndUpdateReadModels: ",
          "Error while fetching or reducing entity snapshot:", error);
    }

    @Test
    void projectionErrorsPropagate() {
      EventStore eventStore = mock(EventStore.class);
      ReadModelStore readModelStore = mock(ReadModelStore.class);
      when(eventStore.fetchEntitySnapshot(any(), any())).thenReturn(Optional.of(SOME_ENTITY_SNAPSHOT));
      IllegalStateException error = new IllegalStateException("projection failed");
      doThrow(error).when(readModelStore).project(SOME_ENTITY_SNAPSHOT);

      assertThatThrownBy(() -> processor.snapshotAndUpdateReadModels(config, "SomeEntity", UUID.of("42"), eventStore,
          readModelStore)).isSameAs(error);
    }
  }

  @Nested
  class DispatchEntityEventsToEventHandlers {

    @Test
    void doesNothingWithoutEventHandlersAndGlobalHandlers() {
      // We try first with a null list of event handlers
      config.eventHandlers.put("SomeEvent", null);
      assertThatCode(() -> processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config))
          .doesNotThrowAnyException();
      // And now with an empty list
      config.eventHandlers.put("SomeEvent", List.of());
      assertThatCode(() -> processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config))
          .doesNotThrowAnyException();

      assertThat(HANDLED).isEmpty();
      verify(processor, never()).getEventInstance(any(), any());
      verify(logger, times(2)).debug(HANDLERS_PREFIX, "No event-handlers found for event SomeEvent. Skipping...");
    }

    @Test
    void callsTheHandlerOfTheEventWithAnEventInstance() {
      config.eventHandlers.put("SomeEvent", List.of(FakeGlobalHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).singleElement().satisfies(handled -> {
        assertThat(handled.handler()).isEqualTo(FakeGlobalHandler.class);
        assertThat(handled.event()).isEqualTo(instanceOfSomeEvent());
      });
    }

    @Test
    void callsAllTheHandlersOfTheEvent() {
      config.eventHandlers.put("SomeEvent", List.of(FakeHandler1.class, FakeHandler2.class, FakeGlobalHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).extracting(Handled::handlerClass)
          .containsExactlyInAnyOrder(FakeHandler1.class, FakeHandler2.class, FakeGlobalHandler.class);
      assertThat(HANDLED).extracting(Handled::event).containsOnly(instanceOfSomeEvent());
      // one event instance is shared by all its handlers
      assertThat(HANDLED.get(0).event()).isSameAs(HANDLED.get(1).event()).isSameAs(HANDLED.get(2).event());
    }

    @Test
    void callsAllTheHandlersOfANotification() {
      config.eventHandlers.put("SomeNotification",
          List.of(FakeHandler1.class, FakeHandler2.class, FakeGlobalHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_NOTIFICATION), config);

      assertThat(HANDLED).extracting(Handled::handlerClass)
          .containsExactlyInAnyOrder(FakeHandler1.class, FakeHandler2.class, FakeGlobalHandler.class);
      assertThat(HANDLED).extracting(Handled::event).containsOnly(new SomeNotification());
    }

    @Test
    void appendsTheGlobalEventHandlersToTheHandlersOfEveryEvent() {
      config.eventHandlers.put("SomeEvent", List.of(FakeHandler1.class));
      config.eventHandlers.put(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS, List.of(FakeGlobalHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT, SOME_NOTIFICATION), config);

      assertThat(HANDLED).extracting(handled -> handled.handler().getSimpleName() + ":" + handled.event())
          .containsExactlyInAnyOrder("FakeHandler1:" + instanceOfSomeEvent(), "FakeGlobalHandler:" + instanceOfSomeEvent(),
              "FakeGlobalHandler:" + new SomeNotification());
      // the registry is not modified
      assertThat(config.eventHandlers.get("SomeEvent")).containsExactly(FakeHandler1.class);
    }

    @Test
    void storesTheEventsOfEveryHandlerWithItsOwnRegister() {
      config.eventHandlers.put("SomeEvent", List.of(FakeHandler1.class, FakeHandler2.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      verify(processor, times(2)).handleRegister(any(), any());
      assertThat(HANDLED).hasSize(2);
      for (Handled handled : HANDLED) {
        verify(processor).handleRegister(config, handled.register());
        assertThat(handled.register().requestID()).isEqualTo(UUID.of("123"));
        assertThat(handled.register().currentUser()).isEqualTo(USER);
        assertThat(handled.register().responseHeaders()).isEmpty();
      }
      assertThat(HANDLED.get(0).register()).isNotSameAs(HANDLED.get(1).register());
    }

    @Test
    void waitsForSlowEventHandlersToFinish() {
      config.eventHandlers.put("SomeEvent", List.of(SlowHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).singleElement().satisfies(handled -> {
        verify(processor).handleRegister(config, handled.register());
        assertThat(handled.register().eventList()).containsExactly(instanceOfSomeEvent());
      });
    }

    @Test
    void continuesProcessingTheOtherEventsWhenAnEventHandlerFails() {
      config.eventHandlers.put("SomeEvent", List.of(FailingHandler.class));
      config.eventHandlers.put("SomeNotification", List.of(FakeHandler1.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT, SOME_NOTIFICATION), config);

      assertThat(HANDLED).extracting(Handled::handlerClass).containsExactlyInAnyOrder(FailingHandler.class, FakeHandler1.class);
      verify(logger).error(eq(HANDLERS_PREFIX),
          argThat(message -> message.toString().matches("Failed to process handlers for event SomeEvent.*")),
          any());
    }

    @Test
    void logsTheFailedReasonsOfAPromisesErrorThrownByAHandler() {
      config.eventHandlers.put("SomeEvent", List.of(PromisesErrorHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).hasSize(1);
      verify(logger).error(eq(HANDLERS_PREFIX), eq("Failed to process handlers for event SomeEvent:"), argThat(value ->
          value instanceof List<?> reasons
              && reasons.size() == 1
              && reasons.getFirst() instanceof PromisesError nested
              && nested.failedReasons().size() == 2
              && nested.failedReasons().get(0).getMessage().equals("Handler 1 failed")
              && nested.failedReasons().get(1).getMessage().equals("Handler 2 failed")));
    }

    @Test
    void runsEveryHandlerOfAnEventEvenIfOneFails() {
      config.eventHandlers.put("SomeEvent", List.of(FailingHandler.class, FakeHandler1.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).extracting(Handled::handlerClass).containsExactlyInAnyOrder(FailingHandler.class, FakeHandler1.class);
      verify(logger).error(eq(HANDLERS_PREFIX), eq("Failed to process handlers for event SomeEvent:"),
          argThat(value -> value instanceof List<?> reasons && reasons.size() == 1
              && ((Throwable) reasons.getFirst()).getMessage().equals("Handler failed")));
      // the failing handler's register is not stored, the other one is
      verify(processor, times(1)).handleRegister(any(), any());
    }

    @Test
    void handlersReceiveEventInstancesWhoseMethodsCanBeCalled() {
      config.eventHandlers.put("SomeEvent", List.of(AnEventHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(GET_PREFIXED_ID_CALLS).hasValue(1);
      verify(logger, never()).error(anyString(), any());
    }

    @Test
    void handlersReceiveTheEventAsStoredWithoutSchemaMigration() {
      config.schemaMigrations.put("SomeEvent", Map.of(2, new SchemaMigrationMetadata(Object.class, "never", 2,
          SomeEvent.class, SomeEvent.class)));
      config.eventHandlers.put("SomeEvent", List.of(FakeHandler1.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).singleElement().extracting(Handled::event).isEqualTo(instanceOfSomeEvent());
    }

    @Test
    void logsWhenTheEventTypeIsNeitherAnEventNorANotification() {
      config.eventHandlers.put("Unknown", List.of(FakeHandler1.class));
      EventEnvelope unknown = SOME_EVENT.toBuilder().typeName("Unknown").build();

      assertThatCode(() -> processor.dispatchEntityEventsToEventHandlers(List.of(unknown, SOME_EVENT), config))
          .doesNotThrowAnyException();

      assertThat(HANDLED).isEmpty();
      verify(logger).error(eq(HANDLERS_PREFIX), eq("Failed to process events:"), argThat(value ->
          value instanceof List<?> reasons && reasons.size() == 1
              && ((Throwable) reasons.getFirst()).getMessage().equals("No @Event or @Notification class is registered"
                  + " for \"Unknown\": cannot create the instance passed to its event handlers")));
    }

    @Test
    void aHandlerClassWithoutHandleMethodFails() {
      config.eventHandlers.put("SomeEvent", List.of(WithoutHandleMethod.class, FakeHandler1.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(HANDLED).extracting(Handled::handlerClass).containsExactly(FakeHandler1.class);
      verify(logger).error(eq(HANDLERS_PREFIX), eq("Failed to process handlers for event SomeEvent:"), any());
    }

    @Test
    void theGlobalErrorHandlerCanSwallowAHandlerErrorAndTheRegisteredEventsAreStored() {
      config.globalErrorsHandler = new GlobalErrorHandlerMetadata(EventHandlerErrors.class);
      swallowHandlerErrors = true;
      config.eventHandlers.put("SomeEvent", List.of(FailingHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(GLOBAL_ERRORS).singleElement().isInstanceOfSatisfying(EventHandlerGlobalError.class, container -> {
        assertThat(container.eventEnvelope()).isEqualTo(SOME_EVENT);
        assertThat(container.eventInstance()).isEqualTo(instanceOfSomeEvent());
        assertThat(container.eventHandlerMetadata()).isNull();
        assertThat(container.originalError()).hasMessage("Handler failed");
      });
      Register register = HANDLED.getFirst().register();
      verify(processor).handleRegister(config, register);
      assertThat(register.eventList()).containsExactly(new SomeEvent(UUID.of("registered before failing")));
      verify(logger, never()).error(eq(HANDLERS_PREFIX), any(), any());
    }

    @Test
    void theErrorReturnedByTheGlobalErrorHandlerIsLogged() {
      config.globalErrorsHandler = new GlobalErrorHandlerMetadata(EventHandlerErrors.class);
      config.eventHandlers.put("SomeEvent", List.of(FailingHandler.class));

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      verify(processor, never()).handleRegister(any(), any());
      verify(logger).error(eq(HANDLERS_PREFIX), eq("Failed to process handlers for event SomeEvent:"),
          argThat(value -> value instanceof List<?> reasons
              && ((Throwable) reasons.getFirst()).getMessage().equals("handled: Handler failed")));
    }

    @Test
    void isTraced() {
      BoosterConfig global = Booster.config();
      List<TraceInfo> traced = Collections.synchronizedList(new ArrayList<>());
      global.traceConfiguration.enableFor(TraceActionTypes.EVENT_HANDLERS_PROCESS.value());
      global.traceConfiguration.onStart = (traceConfig, actionType, info) -> traced.add(info);

      processor.dispatchEntityEventsToEventHandlers(List.of(SOME_EVENT), config);

      assertThat(traced).singleElement().satisfies(info -> {
        assertThat(info.className).isEqualTo("BoosterEventProcessor");
        assertThat(info.methodName).isEqualTo("dispatchEntityEventsToEventHandlers");
        assertThat(info.args).containsExactly(List.of(SOME_EVENT), config);
      });
    }
  }

  @Nested
  class CallEventHandler {

    @Test
    void storesTheEventsOfTheHandlerThroughTheRegisterHandler() {
      BoosterEventProcessor real = new BoosterEventProcessor();
      EventStoreBackedConfig.withStore(config);

      real.callEventHandler(SlowHandler.class, instanceOfSomeEvent(), SOME_EVENT, config);

      verify(config.provider().events()).store(argThat(envelopes -> envelopes.size() == 1
          && envelopes.getFirst().requestID().equals(UUID.of("123"))
          && envelopes.getFirst().currentUser().equals(USER)), eq(config));
    }
  }

  @Nested
  class FilterDispatched {

    @Test
    void removesTheEventsThatHaveAlreadyBeenDispatched() {
      EventStore eventStore = mock(EventStore.class);
      EventEnvelope someEventEnvelope = SOME_EVENT.toBuilder().id("event-id").build();
      when(eventStore.storeDispatchedEvent(any())).thenReturn(false);

      List<EventEnvelope> eventsNotDispatched = processor.filterDispatched(config, List.of(someEventEnvelope), eventStore);

      verify(eventStore, times(1)).storeDispatchedEvent(someEventEnvelope);
      assertThat(eventsNotDispatched).isEmpty();
      verify(logger).warn("[Booster]|BoosterEventDispatcher#filterDispatched: ",
          "Event has already been dispatched. Skipping.", someEventEnvelope);
    }

    @Test
    void keepsTheNewEventsInOrder() {
      EventStore eventStore = mock(EventStore.class);
      EventEnvelope first = SOME_EVENT.toBuilder().id("1").build();
      EventEnvelope second = SOME_EVENT.toBuilder().id("2").build();
      EventEnvelope third = SOME_EVENT.toBuilder().id("3").build();
      when(eventStore.storeDispatchedEvent(first)).thenReturn(true);
      when(eventStore.storeDispatchedEvent(second)).thenReturn(false);
      when(eventStore.storeDispatchedEvent(third)).thenReturn(true);

      assertThat(processor.filterDispatched(config, List.of(first, second, third), eventStore))
          .containsExactly(first, third);
    }
  }

  /** Gives a configuration a provider whose event store accepts everything. */
  static final class EventStoreBackedConfig {
    @SuppressWarnings("deprecation")
    static void withStore(BoosterConfig config) {
      ProviderLibrary provider = mock(ProviderLibrary.class);
      ProviderEventsLibrary events = mock(ProviderEventsLibrary.class);
      when(provider.events()).thenReturn(events);
      when(events.store(any(), any())).thenReturn(List.of());
      config.setProvider(provider);
    }
  }
}
