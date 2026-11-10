package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behaviour of the helpers TypeScript does not test: groups, the HTTP service and the command runner. */
class MiscHelpersTest {

  @Nested
  class GroupsTests {
    @Test
    void removesDuplicatesKeepingTheFirstOccurrence() {
      assertThat(Groups.unique(List.of("b", "a", "b", "c", "a"))).containsExactly("b", "a", "c");
      assertThat(Groups.unique(List.of())).isEmpty();
    }
  }

  @Nested
  class HttpServiceTests {
    private HttpServer server;
    private final Map<String, String> received = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/ok", exchange -> {
        received.put("method", exchange.getRequestMethod());
        received.put("contentType", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
        received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "{\"status\":\"UP\"}");
      });
      server.createContext("/unavailable", exchange -> respond(exchange, 503, "{\"status\":\"DOWN\"}"));
      server.createContext("/redirect", exchange -> {
        exchange.getResponseHeaders().add("Location", "/ok");
        respond(exchange, 302, "");
      });
      server.createContext("/slow", exchange -> {
        try {
          Thread.sleep(1500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        respond(exchange, 200, "late");
      });
      server.start();
    }

    @AfterEach
    void stopServer() {
      server.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(bytes);
      }
    }

    private String url(String path) {
      return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void getsWithTheDefaultContentType() {
      HttpService.PostResult result = HttpService.request(url("/ok"));
      assertThat(result).isEqualTo(new HttpService.PostResult(200, "{\"status\":\"UP\"}"));
      assertThat(received).containsEntry("method", "GET").containsEntry("contentType", "application/json");
    }

    @Test
    void postsTheBody() {
      HttpService.request(url("/ok"), HttpService.Method.POST, "{\"query\":\"query { __typename }\"}");
      assertThat(received).containsEntry("method", "POST").containsEntry("body", "{\"query\":\"query { __typename }\"}");
    }

    @Test
    void rejectsUnexpectedStatusCodesUnlessAccepted() {
      assertThatThrownBy(() -> HttpService.request(url("/unavailable")))
          .isInstanceOf(HttpServiceError.class)
          .hasMessage("HTTP status code 503");
      HttpService.PostResult accepted = HttpService.request(url("/unavailable"), HttpService.Method.GET, "",
          HttpService.PostConfiguration.accepting(503));
      assertThat(accepted.status()).isEqualTo(503);
      assertThat(accepted.body()).isEqualTo("{\"status\":\"DOWN\"}");
    }

    @Test
    void doesNotFollowRedirects() {
      assertThatThrownBy(() -> HttpService.request(url("/redirect"))).hasMessage("HTTP status code 302");
    }

    @Test
    void timesOut() {
      assertThatThrownBy(() -> HttpService.request(url("/slow"), HttpService.Method.GET, "",
          new HttpService.PostConfiguration(null, 200, null)))
          .isInstanceOf(HttpServiceError.class)
          .hasMessage("Request time out");
    }

    @Test
    void reportsConnectionFailures() {
      int port = server.getAddress().getPort();
      server.stop(0);
      assertThatThrownBy(() -> HttpService.request("http://127.0.0.1:" + port + "/ok"))
          .isInstanceOf(HttpServiceError.class)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class RunCommandTests {
    @TempDir Path directory;

    @Test
    void splitsCommandsOnSingleSpacesLikeJavaScript() {
      assertThat(RunCommand.splitCommand("slsart invoke --data '{\"a\": 1}'  -D"))
          .containsExactly("slsart", "invoke", "--data", "'{\"a\":", "1}'", "", "-D");
    }

    @Test
    void returnsTheStandardOutputOfASuccessfulCommand() throws IOException {
      Files.writeString(directory.resolve("file.txt"), "x");
      assertThat(RunCommand.runCommand(directory.toString(), "ls", true)).contains("file.txt");
      assertThat(RunCommand.runCommand(directory.toString(), "echo hello world", true)).isEqualTo("hello world\n");
    }

    @Test
    void throwsTheStandardErrorOfAFailedCommand() {
      assertThatThrownBy(() -> RunCommand.runCommand(directory.toString(), "ls does-not-exist", true))
          .isInstanceOf(RunCommandError.class)
          .satisfies(error -> {
            RunCommandError failure = (RunCommandError) error;
            assertThat(failure.exitCode()).isNotZero();
            assertThat(failure.getMessage()).isEqualTo(failure.stderr()).contains("does-not-exist");
          });
    }

    @Test
    void reportsCommandsThatCannotBeStarted() {
      assertThatThrownBy(() -> RunCommand.runCommand(directory.toString(), "booster-no-such-executable", true))
          .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void returnsTheLiveProcessForAsyncCommands() throws Exception {
      Process process = RunCommand.runCommandAsync(directory.toString(), "echo async", true);
      assertThat(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("async\n");
      assertThat(process.waitFor()).isZero();
    }
  }
}
