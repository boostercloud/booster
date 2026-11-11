package com.boostercloud.framework.types.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Behaviour of {@code errors.ts} and {@code errors/*.ts}: no TypeScript test exists, these follow the behaviour
 * verified on the TypeScript code and the provider tests that pin {@code httpStatusCodeFor} and {@code toClassTitle}.
 */
class ErrorsTest {

  static class MyNotFound extends NotFoundError {
    MyNotFound(String message) {
      super(message);
    }
  }

  /** A class unrelated to Booster's that happens to be named like a Booster error. */
  static class NotFoundErrorLookalike {
    static final class NotFoundError extends RuntimeException {}
  }

  @Nested
  class BoosterErrorBasics {
    @Test
    void usesTheRuntimeClassNameAsDefaultCode() {
      assertThat(new BoosterError("m").code()).isEqualTo("BoosterError");
      assertThat(new NotFoundError("x").code()).isEqualTo("NotFoundError");
      assertThat(new MyNotFound("x").code()).isEqualTo("MyNotFound");
      assertThat(new InvalidReducerError("m", "event", null).code()).isEqualTo("InvalidReducerError");
    }

    @Test
    void keepsAnExplicitCodeEvenWhenEmpty() {
      assertThat(new BoosterError("m", "").code()).isEmpty();
      assertThat(new BoosterError("m", "CUSTOM").code()).isEqualTo("CUSTOM");
    }

    @Test
    void keepsMessageDataAndCause() {
      RuntimeException cause = new RuntimeException("root");
      BoosterError error = new BoosterError("message", "C", Map.of("a", 1), cause);
      assertThat(error).hasMessage("message").hasCause(cause).isInstanceOf(RuntimeException.class);
      assertThat(error.data()).containsEntry("a", 1);
      assertThat(new InvalidParameterError("m").data()).isNull();
    }

    @Test
    void carriesTheReducerInstances() {
      InvalidReducerError error = new InvalidReducerError("No reducer registered for event E", "event", "snapshot");
      assertThat(error.eventInstance()).isEqualTo("event");
      assertThat(error.snapshotInstance()).isEqualTo("snapshot");
      assertThat(error.data()).isNull();
    }
  }

  @Nested
  class HttpStatusCodeFor {
    @Test
    void mapsBoosterErrorsByExactClassName() {
      assertThat(BoosterErrors.httpStatusCodeFor(new InvalidParameterError("e"))).isEqualTo(400);
      assertThat(BoosterErrors.httpStatusCodeFor(new InvalidProtocolError("e"))).isEqualTo(400);
      assertThat(BoosterErrors.httpStatusCodeFor(new NotAuthorizedError("e"))).isEqualTo(401);
      assertThat(BoosterErrors.httpStatusCodeFor(new BoosterTokenExpiredError("e"))).isEqualTo(401);
      assertThat(BoosterErrors.httpStatusCodeFor(new BoosterTokenNotBeforeError("e"))).isEqualTo(401);
      assertThat(BoosterErrors.httpStatusCodeFor(new NotFoundError("e"))).isEqualTo(404);
      assertThat(BoosterErrors.httpStatusCodeFor(new InvalidVersionError("e"))).isEqualTo(422);
    }

    @Test
    void returns500ForEverythingElseIncludingSubclasses() {
      assertThat(BoosterErrors.httpStatusCodeFor(new BoosterError("e"))).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(
          new OptimisticConcurrencyUnexpectedVersionError("e"))).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(new InvalidEventError("e"))).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(new InvalidReducerError("e", null, null))).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(new IllegalStateException())).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(new MyNotFound("e"))).isEqualTo(500);
      assertThat(BoosterErrors.httpStatusCodeFor(null)).isEqualTo(500);
    }

    @Test
    void mapsUnrelatedClassesWithABoosterErrorName() {
      assertThat(BoosterErrors.httpStatusCodeFor(
          new NotFoundErrorLookalike.NotFoundError())).isEqualTo(404);
    }

    @Test
    void namesAndPrintsErrorsLikeJavaScript() {
      assertThat(BoosterErrors.errorName(new NotFoundError("x"))).isEqualTo("NotFoundError");
      assertThat(BoosterErrors.errorToString(new IllegalStateException("boom")))
          .isEqualTo("IllegalStateException: boom");
      assertThat(BoosterErrors.errorToString(new IllegalStateException()))
          .isEqualTo("IllegalStateException");
      assertThat(BoosterErrors.errorToString(null)).isEqualTo("undefined");
    }
  }

  @Nested
  class ToClassTitle {
    @ParameterizedTest
    @CsvSource({
      "InvalidParameterError, Invalid Parameter Error",
      "NotAuthorizedError, Not Authorized Error",
      "BoosterTokenExpiredError, Booster Token Expired Error",
      "BoosterTokenNotBeforeError, Booster Token Not Before Error",
      "NotFoundError, Not Found Error",
      "InvalidVersionError, Invalid Version Error",
      "InvalidProtocolError, Invalid Protocol Error",
      "OptimisticConcurrencyUnexpectedVersionError, Optimistic Concurrency Unexpected Version Error",
      "Error, Error",
      "TypeError, Type Error",
      "GraphQLError, Graph QL Error",
      "HTTPError, HTTP Error",
      "MyHTTPError, My HTTP Error",
      "aB, a B",
      "ABc, A Bc",
      "X, X",
      "URLError2Big, URL Error2 Big",
      "Invalid_ParamError, Invalid_ Param Error",
    })
    void humanizesClassNames(String className, String title) {
      assertThat(TypeLevel.toClassTitle(className)).isEqualTo(title);
    }

    @Test
    void humanizesTheClassOfAnInstance() {
      assertThat(TypeLevel.toClassTitle(new NotFoundError("x"))).isEqualTo("Not Found Error");
      assertThat(TypeLevel.toClassTitle("")).isEmpty();
    }
  }

  @Nested
  class GlobalErrorContainers {
    @Test
    void keepTheContextAndTheOriginalError() {
      RuntimeException original = new RuntimeException("x");
      CommandEnvelope envelope = CommandEnvelope.builder().typeName("C").version(1).value(Map.of()).build();
      CommandMetadata metadata = new CommandMetadata(String.class, List.of(), List.of(), (user, command) -> {}, null);
      CommandHandlerGlobalError command = new CommandHandlerGlobalError(envelope, metadata, original);
      assertThat(command.commandEnvelope()).isSameAs(envelope);
      assertThat(command.commandMetadata()).isSameAs(metadata);
      assertThat(command.originalError()).isSameAs(original);
      ReducerGlobalError reducer = new ReducerGlobalError(
          EventEnvelope.builder().build(), "event", null, new ReducerMetadata(String.class, "reduce"), original);
      assertThat(reducer.eventInstance()).isEqualTo("event");
      assertThat(reducer.snapshotInstance()).isNull();
      assertThat(reducer.reducerMetadata().methodName()).isEqualTo("reduce");
    }

    @Test
    void canBeDispatchedByExactClassWithASwitch() {
      RuntimeException original = new RuntimeException("x");
      List<GlobalErrorContainer> containers = List.of(
          new GlobalErrorContainer(original),
          new EventGlobalError(EventEnvelope.builder().build(), original),
          new QueryHandlerGlobalError(null, original),
          new EventHandlerGlobalError(null, "instance", null, original));
      List<String> hooks = containers.stream().map(container -> switch (container) {
        case CommandHandlerGlobalError c -> "onCommandHandlerError";
        case QueryHandlerGlobalError c -> "onQueryHandlerError";
        case ScheduleCommandGlobalError c -> "onScheduledCommandHandlerError";
        case EventHandlerGlobalError c -> "onDispatchEventHandlerError";
        case ReducerGlobalError c -> "onReducerError";
        case ProjectionGlobalError c -> "onProjectionError";
        case SnapshotPersistHandlerGlobalError c -> "onSnapshotPersistError";
        case EventGlobalError c -> "onEventError";
        default -> "onError";
      }).toList();
      assertThat(hooks).containsExactly("onError", "onEventError", "onQueryHandlerError", "onDispatchEventHandlerError");
    }
  }
}
