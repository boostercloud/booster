package com.boostercloud.cli.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The jar's entry point: an older JVM gets a clear message instead of an {@code UnsupportedClassVersionError}. */
class JavaVersionCheckTest {
  @Test
  void readsTheFeatureReleaseOfEveryVersionScheme() {
    assertThat(JavaVersionCheck.featureVersion("1.8")).isEqualTo(8);
    assertThat(JavaVersionCheck.featureVersion("9")).isEqualTo(9);
    assertThat(JavaVersionCheck.featureVersion("21")).isEqualTo(21);
    assertThat(JavaVersionCheck.featureVersion("27")).isEqualTo(27);
    assertThat(JavaVersionCheck.featureVersion("27.1")).isEqualTo(27);
    // unknown: the CLI is started and the JVM decides
    assertThat(JavaVersionCheck.featureVersion(null)).isEqualTo(Integer.MAX_VALUE);
    assertThat(JavaVersionCheck.featureVersion("next")).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void explainsWhatIsWrongOnAnOlderJava() {
    assertThat(JavaVersionCheck.problem("21", "/jdks/temurin-21", 27))
        .isEqualTo(
            "boost: Java 27 or newer is required, but found Java 21 at /jdks/temurin-21. Set JAVA_HOME to a JDK 27 (or"
                + " newer) installation.");
    assertThat(JavaVersionCheck.problem("1.8", "/jdks/8", 27)).contains("but found Java 1.8 at /jdks/8");
  }

  @Test
  void acceptsTheRequiredReleaseAndNewerOnes() {
    assertThat(JavaVersionCheck.problem("27", "/jdks/27", 27)).isNull();
    assertThat(JavaVersionCheck.problem("28", "/jdks/28", 27)).isNull();
    assertThat(JavaVersionCheck.problem(null, null, 27)).isNull();
  }

  @Test
  void requiresTheReleaseTheCliWasCompiledFor() throws IOException {
    int required = JavaVersionCheck.requiredRelease();
    assertThat(required).isEqualTo(majorVersion("/com/boostercloud/cli/Main.class") - 44).isGreaterThanOrEqualTo(27);
    assertThat(JavaVersionCheck.problem(System.getProperty("java.specification.version"), "", required)).isNull();
  }

  @Test
  void isLoadableByJava8() throws IOException {
    assertThat(majorVersion("/com/boostercloud/cli/launcher/JavaVersionCheck.class")).isEqualTo(52);
  }

  private static int majorVersion(String classResource) throws IOException {
    try (InputStream in = JavaVersionCheckTest.class.getResourceAsStream(classResource)) {
      assertThat(in).as(classResource).isNotNull();
      DataInputStream data = new DataInputStream(in);
      assertThat(data.readInt()).isEqualTo(0xCAFEBABE);
      data.readUnsignedShort();
      return data.readUnsignedShort();
    }
  }
}
