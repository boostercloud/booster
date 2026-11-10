package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.errors.CommandHandlerGlobalError;
import com.boostercloud.framework.types.errors.EventGlobalError;
import com.boostercloud.framework.types.errors.EventHandlerGlobalError;
import com.boostercloud.framework.types.errors.GlobalErrorContainer;
import com.boostercloud.framework.types.errors.ProjectionGlobalError;
import com.boostercloud.framework.types.errors.QueryHandlerGlobalError;
import com.boostercloud.framework.types.errors.ReducerGlobalError;
import com.boostercloud.framework.types.errors.ScheduleCommandGlobalError;
import com.boostercloud.framework.types.errors.SnapshotPersistHandlerGlobalError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-global-error-dispatcher.test.ts}, plus every row of the outcome matrix (spec 05 §3.6). */
class BoosterGlobalErrorDispatcherTest {

  static final List<Object> CALLS = new ArrayList<>();
  static final Throwable ERROR = new IllegalStateException("test");

  static Throwable wrap(Throwable error, String hook) {
    return new IllegalStateException(error.getMessage() + "." + hook, error);
  }

  public static class GenericOnly {
    public static Throwable onScheduledCommandHandlerError(Throwable error) {
      return wrap(error, "onScheduledCommandHandlerError");
    }

    public static Throwable onError(Throwable error) {
      CALLS.add(error);
      return wrap(error, "onError");
    }
  }

  public static class ThrowingScheduled {
    public static Throwable onScheduledCommandHandlerError(Throwable error) {
      throw new IllegalStateException("failed");
    }

    public static Throwable onError(Throwable error) {
      CALLS.add(error);
      return error;
    }
  }

  public static class AllSpecific {
    public static Throwable onCommandHandlerError(Throwable error, CommandEnvelope envelope, CommandMetadata metadata) {
      CALLS.add(java.util.Arrays.asList("command", envelope, metadata));
      return wrap(error, "onCommandHandlerError");
    }

    public static Throwable onQueryHandlerError(Throwable error, QueryEnvelope query) {
      CALLS.add(List.of("query", query));
      return wrap(error, "onQueryHandlerError");
    }

    public static Throwable onDispatchEventHandlerError(Throwable error, Object eventEnvelope, Object metadata, Object instance) {
      CALLS.add(List.of("eventHandler", eventEnvelope, metadata, instance));
      return wrap(error, "onDispatchEventHandlerError");
    }

    public static Throwable onReducerError(Throwable error, EventEnvelope envelope, ReducerMetadata metadata, Object event,
        Object snapshot) {
      CALLS.add(List.of("reducer", envelope, metadata, event));
      return wrap(error, "onReducerError");
    }

    public static Throwable onProjectionError(Throwable error, EntitySnapshotEnvelope envelope, ProjectionMetadata metadata,
        Object entity, Object readModel) {
      CALLS.add(List.of("projection", envelope, metadata, entity));
      return wrap(error, "onProjectionError");
    }

    public static Throwable onSnapshotPersistError(Throwable error, NonPersistedEntitySnapshotEnvelope snapshot) {
      CALLS.add(List.of("snapshot", snapshot));
      return wrap(error, "onSnapshotPersistError");
    }

    public static Throwable onEventError(Throwable error, EventEnvelope envelope) {
      CALLS.add(List.of("event", envelope));
      return wrap(error, "onEventError");
    }
  }

  public static class IgnoringProjection {
    public static Throwable onProjectionError(Throwable error) {
      return null;
    }

    public static Throwable onError(Throwable error) {
      CALLS.add(error);
      return error;
    }
  }

  public static class IgnoringGeneric {
    public static Throwable onScheduledCommandHandlerError(Throwable error) {
      return wrap(error, "onScheduledCommandHandlerError");
    }

    public static Throwable onError(Throwable error) {
      return null;
    }
  }

  public static class OnlyOnError {
    public static Throwable onError(Throwable error) {
      CALLS.add(error);
      return wrap(error, "onError");
    }
  }

  public static class Empty {}

  public static class ThrowingOnError {
    public static Throwable onError(Throwable error) {
      throw new IllegalStateException("onError failed");
    }
  }

  private BoosterConfig config;
  private Logger logger;

  @BeforeEach
  void setUp() {
    CALLS.clear();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
  }

  private BoosterGlobalErrorDispatcher dispatcher(Class<?> handler) {
    config.globalErrorsHandler = new GlobalErrorHandlerMetadata(handler);
    return new BoosterGlobalErrorDispatcher(config);
  }

  private ScheduleCommandGlobalError scheduled() {
    return new ScheduleCommandGlobalError(ScheduledCommandEnvelope.builder().typeName("S").build(),
        new ScheduledCommandMetadata(Object.class, null), ERROR);
  }

  @Test
  void returnsTheOriginalErrorWithoutAHandler() {
    assertThat(new BoosterGlobalErrorDispatcher(config).dispatch(new GlobalErrorContainer(ERROR))).isSameAs(ERROR);
  }

  @Test
  void callsOnlyOnErrorForABareContainer() {
    Throwable result = dispatcher(GenericOnly.class).dispatch(new GlobalErrorContainer(ERROR));

    assertThat(result).hasMessage("test.onError");
    assertThat(CALLS).containsExactly(ERROR);
  }

  @Test
  void returnsTheErrorThrownByASpecificHookWithoutCallingOnError() {
    Throwable result = dispatcher(ThrowingScheduled.class).dispatch(scheduled());

    assertThat(result).hasMessage("failed");
    assertThat(CALLS).isEmpty();
    verify(logger).error(anyString(),
        eq("Unhandled error inside the global error handler. When handling error Error: test"), same(result));
  }

  @Test
  void chainsTheSpecificHookAndOnError() {
    Throwable result = dispatcher(GenericOnly.class).dispatch(scheduled());

    assertThat(result).hasMessage("test.onScheduledCommandHandlerError.onError");
    assertThat(CALLS).singleElement().satisfies(error -> assertThat((Throwable) error).hasMessage("test.onScheduledCommandHandlerError"));
  }

  @Test
  void callsEverySpecificHookWithItsArguments() {
    BoosterGlobalErrorDispatcher dispatcher = dispatcher(AllSpecific.class);
    CommandEnvelope command = CommandEnvelope.builder().typeName("C").version(1).build();
    QueryEnvelope query = QueryEnvelope.builder().typeName("Q").version(1).build();
    EventEnvelope event = EventEnvelope.builder().typeName("E").build();
    ReducerMetadata reducer = new ReducerMetadata(Object.class, "reduce");
    EntitySnapshotEnvelope snapshot = EntitySnapshotEnvelope.builder().typeName("S").build();
    ProjectionMetadata projection = new ProjectionMetadata(Object.class, "project", null);

    assertThat(dispatcher.dispatch(new CommandHandlerGlobalError(command, null, ERROR))).hasMessage("test.onCommandHandlerError");
    assertThat(dispatcher.dispatch(new QueryHandlerGlobalError(query, ERROR))).hasMessage("test.onQueryHandlerError");
    assertThat(dispatcher.dispatch(new EventHandlerGlobalError(event, "instance", "metadata", ERROR)))
        .hasMessage("test.onDispatchEventHandlerError");
    assertThat(dispatcher.dispatch(new ReducerGlobalError(event, "instance", null, reducer, ERROR))).hasMessage("test.onReducerError");
    assertThat(dispatcher.dispatch(new ProjectionGlobalError(snapshot, "entity", null, projection, ERROR)))
        .hasMessage("test.onProjectionError");
    assertThat(dispatcher.dispatch(new SnapshotPersistHandlerGlobalError(snapshot.toNonPersisted(), ERROR)))
        .hasMessage("test.onSnapshotPersistError");
    assertThat(dispatcher.dispatch(new EventGlobalError(event, ERROR))).hasMessage("test.onEventError");

    assertThat(CALLS).containsExactly(
        java.util.Arrays.asList("command", command, null),
        List.of("query", query),
        List.of("eventHandler", event, "metadata", "instance"),
        List.of("reducer", event, reducer, "instance"),
        List.of("projection", snapshot, projection, "entity"),
        List.of("snapshot", snapshot.toNonPersisted()),
        List.of("event", event));
    // Without onError, every specific result is logged as unhandled (TypeScript rows 5 of the matrix).
    verify(logger, org.mockito.Mockito.times(7)).error(anyString(), anyString(), any(Throwable.class));
  }

  @Test
  void swallowsTheErrorWhenTheSpecificHookReturnsNull() {
    assertThat(dispatcher(IgnoringProjection.class).dispatch(new ProjectionGlobalError(null, null, null, null, ERROR))).isNull();
    assertThat(CALLS).isEmpty();
  }

  @Test
  void swallowsTheErrorWhenOnErrorReturnsNull() {
    assertThat(dispatcher(IgnoringGeneric.class).dispatch(scheduled())).isNull();
  }

  @Test
  void skipsOnErrorWhenTheSpecificHookIsMissing() {
    Throwable result = dispatcher(OnlyOnError.class).dispatch(scheduled());

    assertThat(result).isSameAs(ERROR);
    assertThat(CALLS).isEmpty();
    verify(logger).error(anyString(), anyString(), same(ERROR));
  }

  @Test
  void returnsTheOriginalErrorWhenThereIsNoHookAtAll() {
    assertThat(dispatcher(Empty.class).dispatch(new GlobalErrorContainer(ERROR))).isSameAs(ERROR);
    verify(logger).error(anyString(), anyString(), same(ERROR));
  }

  @Test
  void returnsTheErrorThrownByOnError() {
    Throwable result = dispatcher(ThrowingOnError.class).dispatch(new GlobalErrorContainer(ERROR));

    assertThat(result).hasMessage("onError failed");
  }

  @Test
  void capturesTheHandlerAtConstruction() {
    BoosterGlobalErrorDispatcher dispatcher = new BoosterGlobalErrorDispatcher(config);
    config.globalErrorsHandler = new GlobalErrorHandlerMetadata(OnlyOnError.class);

    assertThat(dispatcher.dispatch(new GlobalErrorContainer(ERROR))).isSameAs(ERROR);
    assertThat(CALLS).isEmpty();
    verifyNoInteractions(logger);
  }
}
