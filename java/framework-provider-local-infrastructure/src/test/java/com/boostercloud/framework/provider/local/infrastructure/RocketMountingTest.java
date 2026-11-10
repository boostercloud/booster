package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.infrastructure.testrocket.EchoRocket;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Infrastructure rockets: loading, mounting routes on the local server, and unmounting on shutdown. */
class RocketMountingTest {

  private static final String ECHO = "com.boostercloud.framework.provider.local.infrastructure.testrocket";

  @TempDir
  Path directory;

  private ServerFixture fixture;
  private FakeUserApp app;

  @BeforeEach
  void setUp() {
    fixture = new ServerFixture();
    app = new FakeUserApp(fixture.config);
    EchoRocket.EVENTS.clear();
  }

  @AfterEach
  void tearDown() {
    fixture.close();
    UserAppRegistry.clear();
  }

  private RocketDescriptor echo(String prefix) {
    return new RocketDescriptor(ECHO, Map.of("prefix", prefix, "storage", directory.resolve("storage").toString()));
  }

  @Test
  void rocketsMountTheirRoutesWithTheBoundPortInOrder() {
    LocalServer server = fixture.start(directory, app, List.of(echo("hooks"), echo("other")));

    assertThat(EchoRocket.EVENTS).containsExactly("mount hooks port=" + server.port() + " app=my-store",
        "mount other port=" + server.port() + " app=my-store");

    HttpResponse<String> response = Http1Client.postJson(server.url() + "/hooks/github?event=push&ids[]=1",
        "{\"a\":1}");
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("access-control-allow-origin")).hasValue("*");
    assertThat(BoosterJson.parse(response.body())).isEqualTo(Map.of(
        "method", "POST", "url", "/github?event=push&ids[]=1", "originalUrl", "/hooks/github?event=push&ids[]=1",
        "baseUrl", "/hooks", "params", Map.of("name", "github"),
        "query", Map.of("event", "push", "ids", List.of("1")),
        "body", Map.of("a", 1), "rawBody", "{\"a\":1}"));

    HttpResponse<String> raw = Http1Client.send("POST", server.url() + "/other/x",
        Map.of("Content-Type", "application/octet-stream"), "abc");
    assertThat(BoosterJson.parse(raw.body())).extracting(body -> ((Map<?, ?>) body).get("body"))
        .isEqualTo("bytes:YWJj");

    assertThat(Http1Client.get(server.url() + "/hooks/x").statusCode()).isEqualTo(201);
    assertThat(Http1Client.send("DELETE", server.url() + "/hooks/x", Map.of(), null).statusCode()).isEqualTo(404);
  }

  @Test
  void theFrameworkRoutesComeFirst() {
    LocalServer server = fixture.start(directory, app, List.of(echo("graphql")));

    HttpResponse<String> response = Http1Client.postJson(server.url() + "/graphql", "{}");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(app.graphQLRequests).hasSize(1);
  }

  @Test
  void rocketRoutesCanStreamUploadsAndDownloadFiles() {
    LocalServer server = fixture.start(directory, app, List.of(echo("hooks")));
    byte[] content = "line 1\nline 2\n".repeat(10_000).getBytes(StandardCharsets.UTF_8);

    HttpResponse<byte[]> upload = Http1Client.sendBytes("PUT", server.url() + "/files/client1/notes.txt",
        Map.of("Content-Type", "application/octet-stream"), content);
    assertThat(upload.statusCode()).isEqualTo(200);
    assertThat(BoosterJson.parse(new String(upload.body(), StandardCharsets.UTF_8)))
        .isEqualTo(Map.of("status", "success", "size", content.length));
    assertThat(directory.resolve("storage/client1/notes.txt")).hasBinaryContent(content);

    HttpResponse<byte[]> download = Http1Client.sendBytes("GET", server.url() + "/files/client1/notes.txt", Map.of(),
        null);
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.body()).isEqualTo(content);
    assertThat(download.headers().firstValue("content-disposition")).hasValue("attachment; filename=\"notes.txt\"");
    assertThat(download.headers().firstValue("content-type")).hasValue("text/plain; charset=UTF-8");

    HttpResponse<String> missing = Http1Client.get(server.url() + "/files/client1/missing.txt");
    assertThat(missing.statusCode()).isEqualTo(500);
    assertThat(BoosterJson.parse(missing.body())).isEqualTo(Map.of("title", "Unchecked IO Exception",
        "reason", "ENOENT: no such file or directory, stat '"
            + directory.resolve("storage/client1/missing.txt").toAbsolutePath() + "'"));
  }

  @Test
  void rocketsReachTheApplicationThroughTheRegistry() {
    UserAppRegistry.register(app);
    LocalServer server = fixture.start(directory, app, List.of(echo("hooks")));

    HttpResponse<String> response = Http1Client.postJson(server.url() + "/dispatch", "{\"x\":1}");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(app.rocketRequests).hasSize(1);
  }

  @Test
  void rocketsAreUnmountedWhenTheServerStops() {
    LocalServer server = fixture.start(directory, app, List.of(echo("a"), echo("b")));

    server.close();

    assertThat(EchoRocket.EVENTS).endsWith("unmount a", "unmount b");
  }

  @Test
  void aFailingMountAbortsTheStartAndReleasesThePort() throws Exception {
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    var runtime = ServerFixture.runtime(directory, app);
    LocalInfrastructure infrastructure = Infrastructure.infrastructure(
        List.of(echo("first"), new RocketDescriptor(ECHO + ".failing", null)), fixture.options(runtime));

    try {
      assertThatThrownBy(() -> infrastructure.startServer(fixture.config, port))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Undefined route parameter [object Object]");
    } finally {
      runtime.close();
    }
    assertThat(EchoRocket.EVENTS).containsExactly("mount first port=" + port + " app=my-store", "unmount first");
    try (ServerSocket again = new ServerSocket(port)) {
      assertThat(again.getLocalPort()).isEqualTo(port);
    }
  }

  @Test
  void rocketsAreLoadedWhenTheInfrastructureIsCreated() {
    assertThat(Infrastructure.loadRockets(List.of(echo("x")))).singleElement().isInstanceOf(EchoRocket.class);
    assertThat(Infrastructure.loadRockets(null)).isEmpty();
    assertThat(Infrastructure.infrastructure(null).rockets()).isEmpty();
  }

  @Test
  void loadingErrorsKeepTheTypeScriptMessages() {
    assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(new RocketDescriptor("com.acme.missing", null))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not load the rocket infrastructure package com.acme.missing. Make sure you installed it "
            + "in your project devDependencies.");
    assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(new RocketDescriptor(ECHO + ".notbuilder", null))))
        .hasMessage("Could not initialize rocket infrastructure package " + ECHO + ".notbuilder. It doesn't seem to "
            + "implement a rocket builder method as default export.");
    assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(new RocketDescriptor(ECHO + ".nomount", null))))
        .hasMessage("The package " + ECHO + ".nomount doesn't seem to be a rocket.");
    assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(new RocketDescriptor(ECHO + ".wrongtype", null))))
        .hasMessage("The package " + ECHO + ".wrongtype doesn't seem to be a rocket.");
  }
}
