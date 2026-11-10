package com.boostercloud.cli.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.testing.RecordingOraLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/script-service.test.ts}. */
class ScriptTest {
  private static final Map<String, String> TEST_CONTEXT = Map.of("ctxParam", "value");

  private RecordingOraLogger logger;
  private List<String> invocations;

  /** A {@code SyntaxError}, named like the JavaScript error used by the TypeScript test. */
  static final class SyntaxError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SyntaxError(String message) {
      super(message);
    }
  }

  @BeforeEach
  void setUp() {
    ColorSupport.setLevel(0);
    logger = new RecordingOraLogger();
    invocations = new ArrayList<>();
  }

  private Script.Action<Map<String, String>> recording(String name) {
    return ctx -> invocations.add(name + ":" + ctx.get("ctxParam"));
  }

  @Nested
  class Init {
    @Test
    void runsASequenceOfSuccessfulActionsChainingContext() {
      Script.init(logger, "initializing test", () -> TEST_CONTEXT)
          .step("step 1", recording("1"))
          .step("step 2", recording("2"))
          .step("step 3", recording("3"))
          .done();
      assertThat(invocations).containsExactly("1:value", "2:value", "3:value");
    }

    @Test
    void runsASequenceOfSuccessfulActionsAndStopsOnFailure() {
      String errorMsg = "some error";
      assertThatThrownBy(
              () ->
                  Script.init(logger, "initializing test", () -> TEST_CONTEXT)
                      .step("step", recording("1"))
                      .step(
                          "step",
                          ctx -> {
                            invocations.add("2:" + ctx.get("ctxParam"));
                            throw new CliException(errorMsg);
                          })
                      .step("step", recording("3"))
                      .done())
          .isInstanceOf(CliException.class)
          .hasMessageContaining(errorMsg);
      assertThat(invocations).containsExactly("1:value", "2:value");
    }

    @Test
    void printsTheProvidedMessage() {
      Script.init(logger, "initializing", () -> TEST_CONTEXT).done();
      assertThat(logger.texts("info")).containsExactly("initializing");
    }

    @Test
    void failsGracefullyInCaseOfInitializerFailure() {
      assertThatThrownBy(
              () ->
                  Script.init(
                          logger,
                          "initializing",
                          () -> {
                            throw new CliException("some error");
                          })
                      .done())
          .hasMessageContaining("some error");
      assertThat(logger.calls()).isEmpty();
    }

    @Test
    void resolvesTheContextBeforePrintingTheInitialMessage() {
      List<String> order = new ArrayList<>();
      RecordingOraLogger ordered =
          new RecordingOraLogger() {
            @Override
            public com.boostercloud.cli.services.OraLogger info(String message) {
              order.add("info");
              return this;
            }
          };
      Script.init(
              ordered,
              "init",
              () -> {
                order.add("context");
                return TEST_CONTEXT;
              })
          .done();
      assertThat(order).containsExactly("context", "info");
    }

    @Test
    void keepsTheOriginalErrorAsCauseAndUsesItsStackAsMessage() {
      CliException original = new CliException("boom");
      assertThatThrownBy(
              () ->
                  Script.init(logger, "init", () -> TEST_CONTEXT)
                      .step(
                          "step",
                          ctx -> {
                            throw original;
                          })
                      .done())
          .isInstanceOf(CliException.class)
          .hasCause(original)
          .hasMessageStartingWith("Error: boom\n    at ");
    }
  }

  @Nested
  class Info {
    @Test
    void printsTheProvidedMessage() {
      Script.init(logger, "initializing test", () -> TEST_CONTEXT).info("yo!").done();
      assertThat(logger.texts("info")).containsExactly("initializing test", "yo!");
    }
  }

  @Nested
  class Step {
    @Test
    void printsThePassedMessageInitializesAndRunsTheFunction() {
      String msg = "That is one small step for a man";
      Script.init(logger, "initializing", () -> TEST_CONTEXT).step(msg, recording("step")).done();
      assertThat(logger.texts("start")).containsExactly(msg);
      assertThat(logger.texts("succeed")).containsExactly(msg);
      assertThat(invocations).containsExactly("step:value");
    }

    @Test
    void failsGracefullyOnStepFunctionFailure() {
      String msg = "That is no step for anyone";
      assertThatThrownBy(
              () ->
                  Script.init(logger, "initializing", () -> TEST_CONTEXT)
                      .step(
                          msg,
                          ctx -> {
                            invocations.add("step");
                            throw new CliException("some error");
                          })
                      .done())
          .hasMessageContaining("some error");
      assertThat(logger.texts("start")).containsExactly(msg);
      assertThat(logger.texts("succeed")).isEmpty();
      assertThat(logger.texts("fail")).containsExactly(msg);
      assertThat(invocations).containsExactly("step");
    }
  }

  @Nested
  class CatchError {
    @Test
    void printsACustomErrorMessageForSpecifiedErrorTypes() {
      String msg = "much nicer message";
      assertThatThrownBy(
              () ->
                  Script.init(
                          logger,
                          "initializing",
                          () -> {
                            throw new SyntaxError("other message");
                          })
                      .catchError("SyntaxError", e -> msg)
                      .done())
          .hasMessage(msg);
    }

    @Test
    void printsTheErrorMessageForNonSpecifiedErrorTypes() {
      String msg = "some message";
      assertThatThrownBy(
              () ->
                  Script.init(
                          logger,
                          "initializing",
                          () -> {
                            throw new CliException(msg);
                          })
                      .catchError("SyntaxError", e -> "much nicer message")
                      .done())
          .hasMessageContaining(msg)
          .hasMessageNotContaining("much nicer message");
    }

    @Test
    void laterHandlersForTheSameTypeOverwriteEarlierOnes() {
      assertThatThrownBy(
              () ->
                  Script.init(
                          logger,
                          "initializing",
                          () -> {
                            throw new SyntaxError("x");
                          })
                      .catchError("SyntaxError", e -> "first")
                      .catchError("SyntaxError", e -> "second")
                      .done())
          .hasMessage("second");
    }
  }

  @Nested
  class OptionalStep {
    @Test
    void shouldSkipOptionalStepAndLogInfo() {
      String message = "Hello World!";
      Script.init(logger, "Initializing test", () -> TEST_CONTEXT)
          .optionalStep(true, message, recording("step"))
          .done();
      assertThat(logger.texts("start")).isEmpty();
      assertThat(logger.texts("succeed")).isEmpty();
      assertThat(logger.texts("info")).contains(Brand.mellancholize("Skipping: " + message));
      assertThat(invocations).isEmpty();
    }

    @Test
    void shouldNotSkipOptionalStepAndCallPassedAction() {
      String message = "Hello World!";
      Script.init(logger, "Initializing test", () -> TEST_CONTEXT)
          .optionalStep(false, message, recording("step"))
          .done();
      assertThat(logger.texts("start")).containsExactly(message);
      assertThat(logger.texts("succeed")).containsExactly(message);
      assertThat(logger.texts("info")).doesNotContain(Brand.mellancholize("Skipping: " + message));
      assertThat(invocations).containsExactly("step:value");
    }
  }

  @Test
  void scriptsAreImmutable() {
    Script<Map<String, String>> base = Script.init(logger, "init", () -> TEST_CONTEXT);
    base.step("extra", recording("extra"));
    base.done();
    assertThat(invocations).isEmpty();
  }
}
