package io.boostercloud.booster.core.regex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RegexJarIT {
  @TempDir Path temporary;
  @Test void shadedJarRetainsTregexAndGeneratedTruffleLanguageProviders() throws Exception {
    Path root = Path.of("").toAbsolutePath();
    Path jar = root.resolve("target/booster-java-0.1.0-SNAPSHOT-all.jar");
    assertTrue(Files.isRegularFile(jar));
    Path log = temporary.resolve("regex-jar.log");
    Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", root.resolve("target/test-classes") + File.pathSeparator + jar,
        RegexJarProbe.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Packaged regex process timed out");
      String output = Files.readString(log);
      assertEquals(0, process.exitValue(), output); assertTrue(output.contains("regex-jar-ok"), output);
    } finally { if (process.isAlive()) process.destroyForcibly().waitFor(); }
  }
}
