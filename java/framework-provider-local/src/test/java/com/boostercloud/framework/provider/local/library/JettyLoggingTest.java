package com.boostercloud.framework.provider.local.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The framework ships no SLF4J provider (an application chooses its own); without one, Jetty still starts and SLF4J's
 * notice about falling back to its no-operation logger is not printed.
 */
class JettyLoggingTest {

  private static String run(String mode) throws Exception {
    String java = ProcessHandle.current().info().command().orElse(
        Path.of(System.getProperty("java.home"), "bin", "java").toString());
    // the class path of an application without an SLF4J provider: this module's, minus the tests' no-op provider
    String classPath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
        .filter(entry -> !entry.contains("slf4j-nop"))
        .collect(Collectors.joining(File.pathSeparator));
    Process process = new ProcessBuilder(java, "-cp", classPath, JettyLoggingMain.class.getName(), mode)
        .redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
    return output;
  }

  @Test
  void withoutAProviderJettyStartsAndSlf4jPrintsNothing() throws Exception {
    // what SLF4J prints on its own when there is no provider
    assertThat(run("plain")).contains("SLF4J(W): No SLF4J providers were found.");
    String output = run("jetty");
    assertThat(output).contains("STARTED").contains("verbosity=ERROR").doesNotContain("SLF4J");
  }

  @Test
  void aProviderOfTheApplicationIsLeftAlone() {
    // this test JVM has one (the no-op provider of the tests): nothing is changed
    String before = System.getProperty(JettyLogging.SLF4J_INTERNAL_VERBOSITY);
    JettyLogging.prepare();
    assertThat(System.getProperty(JettyLogging.SLF4J_INTERNAL_VERBOSITY)).isEqualTo(before);
  }
}
