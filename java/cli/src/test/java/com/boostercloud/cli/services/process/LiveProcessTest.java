package com.boostercloud.cli.services.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Port of {@code test/services/process/live.test.ts}. The commands are real processes: the JDK's own
 * {@code java} launcher, so that the test does not depend on the operating system.
 */
class LiveProcessTest {
  @TempDir Path dir;

  private final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

  @Test
  void execReturnsTheFormattedOutput() {
    LiveProcess process = new LiveProcess(System::getenv, dir);
    String output = process.exec(List.of(java, "-version"), dir);
    // java -version prints on the standard error
    assertThat(output).startsWith("\nThere were some issues running the command: ").contains("version").endsWith("\n\n\n");
  }

  @Test
  void execPassesTheEnvironmentAndTheWorkingDirectory() throws Exception {
    Path script = dir.resolve("Env.java");
    Files.writeString(
        script,
        "class Env { public static void main(String[] a) { System.out.print(System.getenv(\"BOOSTER_ENV\") + \"@\""
            + " + new java.io.File(\"\").getAbsoluteFile().getName()); } }");
    LiveProcess process = new LiveProcess(() -> Map.of("BOOSTER_ENV", "production"), dir);
    String output = process.exec(List.of(java, script.toString()), dir);
    assertThat(output).isEqualTo("\n\nproduction@" + dir.toFile().getAbsoluteFile().getName() + "\n");
  }

  @Test
  void execFailsWithTheExitCodeAndTheOutput() {
    LiveProcess process = new LiveProcess(System::getenv, dir);
    assertThatThrownBy(() -> process.exec(List.of(java, "-XX:+ThisOptionDoesNotExist", "-version"), dir))
        .isInstanceOf(ProcessError.class)
        .hasMessageStartingWith("Command failed with exit code 1: " + java + " -XX:+ThisOptionDoesNotExist -version")
        .satisfies(
            e -> {
              CommandFailedException failed = (CommandFailedException) ((ProcessError) e).error();
              assertThat(failed.exitCode()).isEqualTo(1);
              assertThat(failed.stderr()).contains("ThisOptionDoesNotExist");
            });
  }

  @Test
  void execFailsWhenTheCommandDoesNotExist() {
    LiveProcess process = new LiveProcess(System::getenv, dir);
    assertThatThrownBy(() -> process.exec("this-command-does-not-exist-anywhere --flag", dir))
        .isInstanceOf(ProcessError.class)
        .hasMessageStartingWith("Command failed with ENOENT: this-command-does-not-exist-anywhere --flag");
  }

  @Test
  void spawnRunsAttachedProcessesAndReturnsTheirExitCode() throws Exception {
    Path script = dir.resolve("Exit.java");
    Files.writeString(script, "class Exit { public static void main(String[] a) { System.exit(3); } }");
    LiveProcess process = new LiveProcess(System::getenv, dir);
    RunningProcess running = process.spawn(List.of(java, script.toString()), dir);
    assertThat(running.waitFor()).isEqualTo(3);
    assertThat(running.isAlive()).isFalse();
  }

  @Test
  void spawnedProcessesCanBeDestroyed() throws Exception {
    Path script = dir.resolve("Sleep.java");
    Files.writeString(
        script, "class Sleep { public static void main(String[] a) throws Exception { Thread.sleep(60_000); } }");
    LiveProcess process = new LiveProcess(System::getenv, dir);
    RunningProcess running = process.spawn(List.of(java, script.toString()), dir);
    assertThat(running.isAlive()).isTrue();
    running.destroy();
    assertThat(running.waitFor()).isNotZero();
  }

  @Test
  void cwdIsTheConfiguredDirectory() {
    assertThat(new LiveProcess(Map::of, dir).cwd()).isEqualTo(dir);
  }
}
