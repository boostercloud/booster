package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.Register;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The deprecated {@code @Returns} (no TypeScript test). */
class ReturnsTest extends BoosterTestSupport {

  @Command(authorize = All.class)
  public record ReturningCommand(String id) {
    @SuppressWarnings("deprecation")
    @Returns(String.class)
    public static String handle(ReturningCommand command, Register register) {
      return command.id();
    }
  }

  @Test
  void printsTheDeprecationNoticeAndRegistersNothing() {
    PrintStream original = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      Decorators.registerClass(ReturningCommand.class);
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(Decorators.RETURNS_DEPRECATION_MESSAGE + System.lineSeparator());
    assertThat(Decorators.RETURNS_DEPRECATION_MESSAGE).startsWith("\n    The usage of the '@Returns' annotation is deprecated,")
        .endsWith("command#returning-a-value\n  ");
    assertThat(Booster.config().commandHandlers).containsOnlyKeys("ReturningCommand");
  }
}
