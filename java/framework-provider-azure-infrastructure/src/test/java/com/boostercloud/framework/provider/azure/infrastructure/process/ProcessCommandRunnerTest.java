package com.boostercloud.framework.provider.azure.infrastructure.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.RunCommandError;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Runs harmless local processes (no Terraform, no Azure). */
@DisabledOnOs(OS.WINDOWS)
class ProcessCommandRunnerTest {

  @TempDir Path directory;

  @Test
  void returns_the_output_and_passes_the_working_directory_and_environment() {
    String output = ProcessCommandRunner.INSTANCE.run(new Command(directory,
        List.of("sh", "-c", "printf '%s:%s' \"$BOOSTER_TEST_VALUE\" \"$(basename \"$PWD\")\""),
        Map.of("BOOSTER_TEST_VALUE", "42"), true));
    assertThat(output).isEqualTo("42:" + directory.getFileName());
  }

  @Test
  void fails_with_the_standard_error() {
    assertThatThrownBy(() -> ProcessCommandRunner.INSTANCE.run(new Command(directory,
        List.of("sh", "-c", "echo out; echo 'it failed' >&2; exit 3"), Map.of(), true)))
        .isInstanceOfSatisfying(RunCommandError.class, error -> {
          assertThat(error.exitCode()).isEqualTo(3);
          assertThat(error.stdout()).isEqualTo("out\n");
          assertThat(error.stderr()).isEqualTo("it failed\n");
        });
  }

  @Test
  void reports_missing_executables() {
    assertThatThrownBy(() -> ProcessCommandRunner.INSTANCE.run(Command.of(directory, "booster-no-such-command x",
        true))).isInstanceOf(UncheckedIOException.class).hasMessageStartingWith("Could not run 'booster-no-such-command x'");
  }

  @Test
  void splits_command_lines_like_typescript() {
    assertThat(Command.of(directory, "az group  show", false).arguments()).containsExactly("az", "group", "", "show");
  }
}
