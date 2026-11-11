package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.testing.TestCli;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/environment.test.ts}. */
class EnvironmentTest {
  @TempDir Path dir;

  private static final String LOG_MESSAGE = "No environment set";

  @Nested
  class CurrentEnvironment {
    @Test
    void returnsTheVariable() {
      CliContext ctx = new TestCli(dir).env("BOOSTER_ENV", "testing").context();
      assertThat(Environment.currentEnvironment(ctx)).isEqualTo("testing");
    }

    @Test
    void returnsNullWhenUnset() {
      assertThat(Environment.currentEnvironment(new TestCli(dir).context())).isNull();
    }
  }

  @Nested
  class InitializeEnvironmentWithVariableSet {
    @Test
    void environmentFlagOverridesTheVariable() {
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "testing");
      assertThat(Environment.initializeEnvironment(cli.context(), cli.context().logger(), "production")).isTrue();
      assertThat(cli.context().environment()).containsEntry("BOOSTER_ENV", "production");
      assertThat(cli.stdout()).doesNotContain(LOG_MESSAGE);
    }

    @Test
    void keepsTheVariableWithoutFlag() {
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "testing");
      assertThat(Environment.initializeEnvironment(cli.context(), cli.context().logger(), null)).isTrue();
      assertThat(cli.context().environment()).containsEntry("BOOSTER_ENV", "testing");
      assertThat(cli.stdout()).doesNotContain(LOG_MESSAGE);
    }
  }

  @Nested
  class InitializeEnvironmentWithoutVariable {
    @Test
    void setsTheEnvironmentFromTheFlag() {
      TestCli cli = new TestCli(dir);
      assertThat(Environment.initializeEnvironment(cli.context(), cli.context().logger(), "production")).isTrue();
      assertThat(cli.context().environment()).containsEntry("BOOSTER_ENV", "production");
      assertThat(cli.stdout()).doesNotContain(LOG_MESSAGE);
    }

    @Test
    void logsAnErrorWithoutFlag() {
      TestCli cli = new TestCli(dir);
      assertThat(Environment.initializeEnvironment(cli.context(), cli.context().logger(), null)).isFalse();
      assertThat(cli.context().environment()).doesNotContainKey("BOOSTER_ENV");
      assertThat(cli.stdout())
          .contains(
              "✖ Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it"
                  + " before running this command. Example usage: `boost deploy -e <environment>`.");
    }

    @Test
    void anEmptyValueCountsAsUnset() {
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "");
      assertThat(Environment.initializeEnvironment(cli.context(), cli.context().logger(), "")).isFalse();
      assertThat(cli.stdout()).contains(LOG_MESSAGE);
    }
  }
}
