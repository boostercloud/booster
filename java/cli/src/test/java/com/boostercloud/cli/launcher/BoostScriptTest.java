package com.boostercloud.cli.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The POSIX launcher {@code bin/boost} with fake {@code java} executables (they print their arguments), so neither a
 * JDK nor the built jar is needed: which Java it picks, and the warning for a {@code JAVA_HOME} without
 * {@code bin/java}.
 */
@DisabledOnOs(OS.WINDOWS)
class BoostScriptTest {
  @TempDir Path dir;

  private Path jar;
  private Path javaHome;
  private Path pathBin;

  @BeforeEach
  void createFakes() throws IOException {
    jar = Files.writeString(dir.resolve("booster-cli.jar"), "");
    javaHome = dir.resolve("jdk");
    fakeJava(javaHome.resolve("bin/java"), "home-java");
    pathBin = dir.resolve("path-bin");
    fakeJava(pathBin.resolve("java"), "path-java");
  }

  @Test
  void runsTheJarWithTheJavaOfJavaHome() {
    Result result = boost(javaHome.toString());
    assertThat(result.stdout())
        .startsWith("home-java -XX:TieredStopAtLevel=1 ")
        .endsWith(" -jar " + jar + " --version\n");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void runsTheJavaOfThePathWithoutJavaHome() {
    Result result = boost(null);
    assertThat(result.stdout()).startsWith("path-java ");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void warnsWhenJavaHomeHasNoJava() {
    Path missing = dir.resolve("no such jdk");
    Result result = boost(missing.toString());
    assertThat(result.stderr())
        .isEqualTo("boost: JAVA_HOME is set to '" + missing + "', which has no bin/java; using java from the PATH.\n");
    assertThat(result.stdout()).startsWith("path-java ");
  }

  private Result boost(String javaHomeValue) {
    ProcessBuilder builder = new ProcessBuilder("/bin/sh", Path.of("bin", "boost").toAbsolutePath().toString(), "--version");
    Map<String, String> env = builder.environment();
    env.clear();
    env.put("PATH", pathBin + ":/usr/bin:/bin");
    env.put("BOOSTER_CLI_JAR", jar.toString());
    if (javaHomeValue != null) {
      env.put("JAVA_HOME", javaHomeValue);
    }
    try {
      Process process = builder.start();
      process.getOutputStream().close();
      byte[] stdout = process.getInputStream().readAllBytes();
      byte[] stderr = process.getErrorStream().readAllBytes();
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
      assertThat(process.exitValue()).isZero();
      return new Result(new String(stdout, StandardCharsets.UTF_8), new String(stderr, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void fakeJava(Path file, String name) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, "#!/bin/sh\necho \"" + name + " $*\"\n");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private record Result(String stdout, String stderr) {}
}
