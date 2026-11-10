package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/global-error-handler.test.ts}. */
class GlobalErrorHandlerTest extends BoosterTestSupport {

  @GlobalErrorHandler
  public static class ErrorHandler {}

  @GlobalErrorHandler
  public static class ErrorHandlerWithHooks {
    public static Throwable onCommandHandlerError(Throwable error, CommandEnvelope envelope, CommandMetadata metadata) {
      return error;
    }

    public static Throwable onScheduledCommandHandlerError(Throwable error) {
      return error;
    }

    public static Throwable onDispatchEventHandlerError(Throwable error) {
      return error;
    }

    public static Throwable onProjectionError(Throwable error) {
      return error;
    }

    public static Throwable onReducerError(Throwable error) {
      return error;
    }
  }

  @Test
  void registersTheGlobalErrorHandler() {
    Decorators.registerClass(ErrorHandler.class);

    assertThat(Booster.config().globalErrorsHandler.type()).isEqualTo(ErrorHandler.class);
  }

  @Test
  void registersAHandlerWithHooks() {
    Decorators.registerClass(ErrorHandlerWithHooks.class);

    assertThat(Booster.config().globalErrorsHandler.type()).isEqualTo(ErrorHandlerWithHooks.class);
  }

  @Test
  void failsWhenThereIsAlreadyOneNamingTheNewClass() {
    Decorators.registerClass(ErrorHandler.class);

    assertThatThrownBy(() -> Decorators.registerClass(ErrorHandlerWithHooks.class))
        .hasMessage("An error handler called ErrorHandlerWithHooks is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }
}
