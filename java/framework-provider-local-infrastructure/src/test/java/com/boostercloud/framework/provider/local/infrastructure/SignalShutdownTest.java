package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code LocalInfrastructure.start} in a real process: it blocks while serving, and SIGTERM (what {@code boost start}
 * forwards on Ctrl-C) stops it gracefully, letting an in-flight request finish.
 */
@DisabledOnOs(OS.WINDOWS)
class SignalShutdownTest {

  private static final Pattern LISTENING = Pattern.compile("Local server listening on http://localhost:(\\d+) ");

  @TempDir
  Path directory;

  @Test
  void sigtermStopsTheServerGracefully() throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Process process = new ProcessBuilder(java, "-cp", classpath, SignalShutdownMain.class.getName(),
        directory.toString()).redirectErrorStream(true).start();
    List<String> output = new CopyOnWriteArrayList<>();
    BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    Thread outputReader = Thread.ofVirtual().start(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.add(line);
          lines.add(line);
        }
      } catch (IOException e) {
        // the process ended
      }
    });
    try {
      int port = -1;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (port < 0 && System.nanoTime() < deadline) {
        String line = lines.poll(1, TimeUnit.SECONDS);
        Matcher matcher = line == null ? null : LISTENING.matcher(line);
        if (matcher != null && matcher.find()) {
          port = Integer.parseInt(matcher.group(1));
        }
      }
      assertThat(port).as("listening line in %s", output).isPositive();
      assertThat(process.isAlive()).isTrue();

      CompletableFuture<HttpResponse<String>> inFlight = HttpClient.newHttpClient().sendAsync(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/graphql"))
              .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
          HttpResponse.BodyHandlers.ofString());
      String line;
      do {
        line = lines.poll(10, TimeUnit.SECONDS);
      } while (line != null && !line.equals("REQUEST RECEIVED"));
      assertThat(line).as("request received in %s", output).isEqualTo("REQUEST RECEIVED");

      // SIGTERM through the process handle: Process.destroy() would also close our end of the output pipe
      assertThat(process.toHandle().destroy()).isTrue();

      HttpResponse<String> response = inFlight.get(20, TimeUnit.SECONDS);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"data\":{\"finished\":true}}");
      assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
      assertThat(process.exitValue()).isEqualTo(143);
      outputReader.join(Duration.ofSeconds(10));
      assertThat(String.join("\n", output))
          .contains("[Booster]|LocalInfrastructure#start:  Stopping the local server on http://localhost:" + port)
          .contains("[Booster]|LocalInfrastructure#start:  Local server stopped");
    } finally {
      process.destroyForcibly();
    }
  }
}
