package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.LocalHttpRequest;
import com.boostercloud.framework.provider.local.library.ScheduledAdapter.LocalScheduleCommandEnvelope;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The local server over HTTP: routes, middleware, error handling and status codes of the TypeScript Express app. */
class LocalServerTest {

  @TempDir
  Path directory;

  private ServerFixture fixture;
  private FakeUserApp app;
  private LocalServer server;

  @BeforeEach
  void setUp() {
    fixture = new ServerFixture();
    app = new FakeUserApp(fixture.config);
    server = fixture.start(directory, app);
  }

  @AfterEach
  void tearDown() {
    fixture.close();
  }

  private static Object json(HttpResponse<String> response) {
    return BoosterJson.parse(response.body());
  }

  @Test
  void servesGraphQLPostsWithTheResultOfTheApplication() {
    app.onGraphQL(request -> ApiAdapter.requestSucceeded(Map.of("data", Map.of("ChangeCartItem", true)),
        Map.of("X-Booster-Header", "ignored")));
    String body = "{\"query\":\"mutation { ChangeCartItem(input: {cartId: \\\"a3c1\\\"}) }\",\"variables\":{}}";

    HttpResponse<String> response = Http1Client.send("POST", server.graphqlURL(),
        Map.of("Content-Type", "application/json", "Authorization", "Bearer token"), body);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response)).isEqualTo(Map.of("data", Map.of("ChangeCartItem", true)));
    assertThat(response.headers().firstValue("content-type")).hasValue("application/json; charset=utf-8");
    assertThat(response.headers().firstValue("access-control-allow-origin")).hasValue("*");
    assertThat(response.headers().firstValue("access-control-allow-headers"))
        .hasValue("Origin, X-Requested-With, Content-Type, Accept");
    assertThat(response.headers().firstValue("x-booster-header")).isEmpty();
    assertThat(response.headers().firstValue("server")).isEmpty();

    LocalHttpRequest received = (LocalHttpRequest) app.graphQLRequests.get(0);
    assertThat(received.method()).isEqualTo("POST");
    assertThat(received.url()).isEqualTo("/");
    assertThat(received.authorization()).isEqualTo("Bearer token");
    assertThat(received.header("content-type")).isEqualTo("application/json");
    assertThat(received.body()).isEqualTo(Map.of("query", "mutation { ChangeCartItem(input: {cartId: \"a3c1\"}) }",
        "variables", Map.of()));
    assertThat(new String(received.rawBody(), StandardCharsets.UTF_8)).isEqualTo(body);
  }

  @Test
  void graphQLTolerantPaths() {
    assertThat(Http1Client.postJson(server.graphqlURL() + "/", "{}").statusCode()).isEqualTo(200);
    assertThat(Http1Client.postJson(server.url() + "/GraphQL?x=1", "{}").statusCode()).isEqualTo(200);
    assertThat(((LocalHttpRequest) app.graphQLRequests.get(1)).url()).isEqualTo("/?x=1");
  }

  @Test
  void bodiesThatAreNotJsonReachTheApplicationAsAnEmptyObject() {
    Http1Client.send("POST", server.graphqlURL(), Map.of("Content-Type", "text/plain"), "query { x }");
    Http1Client.send("POST", server.graphqlURL(), Map.of(), null);

    assertThat(((LocalHttpRequest) app.graphQLRequests.get(0)).body()).isEqualTo(Map.of());
    assertThat(((LocalHttpRequest) app.graphQLRequests.get(0)).rawBody()).isNull();
    assertThat(((LocalHttpRequest) app.graphQLRequests.get(1)).body()).isEqualTo(Map.of());
  }

  @Test
  void urlencodedBodiesAreParsed() {
    Http1Client.send("POST", server.graphqlURL(), Map.of("Content-Type", "application/x-www-form-urlencoded"),
        "query=query+%7B+x+%7D&variables[a]=1");

    assertThat(((LocalHttpRequest) app.graphQLRequests.get(0)).body())
        .isEqualTo(Map.of("query", "query { x }", "variables", Map.of("a", "1")));
  }

  @Test
  void errorsOfTheApplicationAreAnsweredWithTheirStatusTitleAndReason() {
    app.onGraphQL(request -> {
      throw new NotFoundError("No such cart");
    });

    HttpResponse<String> response = Http1Client.postJson(server.graphqlURL(), "{}");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(json(response)).isEqualTo(Map.of("title", "Not Found Error", "reason", "No such cart"));
    assertThat(response.headers().firstValue("access-control-allow-origin")).hasValue("*");
    verify(fixture.logger).error(eq("[Booster]|local-infrastructure#finalHandler: "), any(NotFoundError.class));
  }

  @Test
  void otherErrorsAreInternalErrors() {
    app.onGraphQL(request -> {
      throw new IllegalArgumentException("Wrong");
    });

    HttpResponse<String> response = Http1Client.postJson(server.graphqlURL(), "{}");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(json(response)).isEqualTo(Map.of("title", "Illegal Argument Exception", "reason", "Wrong"));
  }

  @Test
  void malformedJsonIsAnInternalErrorWithoutCorsHeaders() {
    HttpResponse<String> response = Http1Client.postJson(server.graphqlURL(), "{\"query\":");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(json(response)).isEqualTo(Map.of("title", "Syntax Error", "reason", "Unexpected end of JSON input"));
    assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
    assertThat(app.graphQLRequests).isEmpty();
    verify(fixture.logger).error(eq("[Booster]|local-infrastructure#defaultErrorHandler: "),
        any(com.boostercloud.framework.provider.local.infrastructure.web.SyntaxError.class));
  }

  @Test
  void jsonBodiesAreLimitedToSixMegabytes() {
    String big = "{\"a\":\"" + "x".repeat(6 * 1024 * 1024) + "\"}";

    HttpResponse<String> response = Http1Client.postJson(server.graphqlURL(), big);

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(json(response)).isEqualTo(Map.of("title", "Payload Too Large Error",
        "reason", "request entity too large"));
    assertThat(app.graphQLRequests).isEmpty();

    String almost = "{\"a\":\"" + "x".repeat(6 * 1024 * 1024 - 10) + "\"}";
    assertThat(Http1Client.postJson(server.graphqlURL(), almost).statusCode()).isEqualTo(200);
  }

  @Test
  void unknownRoutesGetExpressNotFoundPage() {
    HttpResponse<String> get = Http1Client.get(server.graphqlURL());
    HttpResponse<String> other = Http1Client.postJson(server.url() + "/graphqlx/a%20b", "{}");

    assertThat(get.statusCode()).isEqualTo(404);
    assertThat(get.body()).contains("<pre>Cannot GET /graphql</pre>");
    assertThat(get.headers().firstValue("content-type")).hasValue("text/html; charset=utf-8");
    assertThat(get.headers().firstValue("access-control-allow-origin")).hasValue("*");
    assertThat(other.statusCode()).isEqualTo(404);
    assertThat(other.body()).contains("<pre>Cannot POST /graphqlx/a%20b</pre>");
  }

  @Test
  void corsPreflightRequestsAreAnsweredDirectly() {
    HttpResponse<String> response = Http1Client.send("OPTIONS", server.graphqlURL(),
        Map.of("Origin", "http://example.com", "Access-Control-Request-Method", "POST",
            "Access-Control-Request-Headers", "content-type,authorization"), null);

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertThat(response.headers().firstValue("access-control-allow-origin")).hasValue("*");
    assertThat(response.headers().firstValue("access-control-allow-methods")).hasValue("GET,HEAD,PUT,PATCH,POST,DELETE");
    assertThat(response.headers().firstValue("access-control-allow-headers")).hasValue("content-type,authorization");
    assertThat(response.headers().firstValue("vary")).hasValue("Access-Control-Request-Headers");
    assertThat(app.graphQLRequests).isEmpty();
  }

  @Test
  void healthRequestsGetTheComponentPathRelativeToTheEndpoint() {
    List<Map<String, Object>> results = List.of(Map.of("id", "booster", "status", "UP"));
    app.onHealth(request -> ApiAdapter.healthRequestResult(results, true));

    HttpResponse<String> root = Http1Client.get(server.healthURL());
    HttpResponse<String> bare = Http1Client.get(server.url() + "/sensor/health");
    HttpResponse<String> component = Http1Client.send("GET", server.healthURL() + "booster/database",
        Map.of("Authorization", "Bearer abc"), null);

    assertThat(root.statusCode()).isEqualTo(200);
    assertThat(json(root)).isEqualTo(results);
    assertThat(bare.statusCode()).isEqualTo(200);
    assertThat(component.statusCode()).isEqualTo(200);
    assertThat(app.healthRequests).extracting(request -> ((LocalHttpRequest) request).url())
        .containsExactly("/", "/", "/booster/database");
    assertThat(((LocalHttpRequest) app.healthRequests.get(2)).authorization()).isEqualTo("Bearer abc");
  }

  @Test
  void unhealthyApplicationsStillAnswer200() {
    app.onHealth(request -> ApiAdapter.healthRequestResult(List.of(Map.of("status", "DOWN")), false));
    assertThat(Http1Client.get(server.healthURL()).statusCode()).isEqualTo(200);
  }

  @Test
  void healthFailuresAnswerTheirCodeWithOnlyTheTitle() {
    app.onHealth(request -> ApiAdapter.requestFailed(new NotAuthorizedError("Access denied")));

    HttpResponse<String> response = Http1Client.get(server.healthURL());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(json(response)).isEqualTo(Map.of("title", "Not Authorized Error"));
  }

  @Test
  void headRequestsGetHeadersWithoutBody() {
    HttpResponse<String> response = Http1Client.send("HEAD", server.healthURL(), Map.of(), null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEmpty();
    assertThat(response.headers().firstValue("content-type")).hasValue("application/json; charset=utf-8");
  }

  @Test
  void healthIsOnlyServedForGetAndHead() {
    assertThat(Http1Client.postJson(server.healthURL(), "{}").statusCode()).isEqualTo(404);
  }

  @Test
  void logsAListeningLineAndPublishesThePort() {
    verify(fixture.logger).info(eq("[Booster]|LocalInfrastructure#start: "), argThat((Object message) ->
        message.toString().equals("Local server listening on http://localhost:" + server.port() + " (GraphQL: "
            + server.graphqlURL() + ", health: " + server.healthURL() + ", websocket: " + server.websocketURL()
            + ")")));
    assertThat(InternalInfo.boosterLocalPort()).isEqualTo(Integer.toString(server.port()));
    assertThat(server.isRunning()).isTrue();
    assertThat(server.runtime().webSocketServerAdapter().isRunning()).isTrue();
  }

  @Test
  void aTakenPortFailsTheStart() {
    var runtime = ServerFixture.runtime(directory.resolve("other"), new FakeUserApp(fixture.config));
    LocalInfrastructure infrastructure = Infrastructure.infrastructure(List.of(), fixture.options(runtime));

    try {
      assertThatThrownBy(() -> infrastructure.startServer(fixture.config, server.port()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("Could not listen on port " + server.port() + ": ");
    } finally {
      runtime.close();
    }
  }

  @Test
  void theSchedulerTriggersScheduledCommandsOnceListening() {
    fixture.config.scheduledCommandHandlers.put("CheckCarts", new ScheduledCommandMetadata(Object.class,
        new ScheduleInterface("0/5", null, null, null, null, null)));
    FakeUserApp scheduledApp = new FakeUserApp(fixture.config);
    LocalServer scheduled = fixture.start(directory.resolve("scheduled"), scheduledApp);
    assertThat(scheduled.scheduler().jobs()).hasSize(1);
    await().until(() -> fixture.clock.sleepers() == 1);

    fixture.clock.advance(Duration.ofMinutes(2));

    await().until(() -> scheduledApp.scheduledTriggers.size() == 1);
    assertThat(scheduledApp.scheduledTriggers.get(0)).isEqualTo(new LocalScheduleCommandEnvelope("CheckCarts"));
  }

  @Test
  void requestsAreHandledConcurrentlyOnVirtualThreads() {
    java.util.concurrent.CountDownLatch together = new java.util.concurrent.CountDownLatch(20);
    List<String> threads = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    app.onGraphQL(request -> {
      threads.add(Thread.currentThread().isVirtual() ? "virtual" : "platform");
      together.countDown();
      try {
        together.await(10, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return ApiAdapter.requestSucceeded(Map.of(), Map.of());
    });

    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      List<java.util.concurrent.Future<Integer>> calls = java.util.stream.IntStream.range(0, 20)
          .mapToObj(i -> executor.submit(() -> Http1Client.postJson(server.graphqlURL(), "{}").statusCode()))
          .toList();
      assertThat(calls).allSatisfy(call -> assertThat(call.get()).isEqualTo(200));
    }
    assertThat(threads).hasSize(20).containsOnly("virtual");
  }
}
