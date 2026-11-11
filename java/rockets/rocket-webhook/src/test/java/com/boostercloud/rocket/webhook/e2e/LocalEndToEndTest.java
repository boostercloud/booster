package com.boostercloud.rocket.webhook.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.provider.local.LocalProvider;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Provider;
import com.boostercloud.framework.provider.local.infrastructure.Infrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalServer;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.webhook.e2e.app.App;
import com.boostercloud.rocket.webhook.e2e.app.Webhooks;
import com.boostercloud.rocket.webhook.support.Http;
import com.boostercloud.rocket.webhook.support.MultipartBody;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end, in-process, with the real pieces: core ({@code Booster.start} on the {@code e2e.app} application), the
 * local provider over a temporary project directory and the local infrastructure server with the webhook rocket
 * mounted (free ports for HTTP and the websocket server). The clients are plain HTTP, as an external service calling
 * the webhooks would be, and the application tester's GraphQL client and tokens: a webhook call registers events that
 * end up in read models visible through GraphQL.
 */
class LocalEndToEndTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private static final String ORDER = """
      query OrderReadModel($id: ID!) {
        OrderReadModel(id: $id) { id product quantity status source }
      }""";
  private static final String CONFIRM = """
      mutation ConfirmOrder($orderId: ID!) {
        ConfirmOrder(input: { orderId: $orderId })
      }""";
  private static final String DOCUMENT = """
      query DocumentReadModel($id: ID!) {
        DocumentReadModel(id: $id) { id title fileName mimeType size sha256 }
      }""";

  @TempDir
  Path project;

  private BoosterConfig config;
  private LocalServer server;
  private GraphQLClient client;
  private ApplicationTester applicationUnderTest;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, "local");
    Booster.start(App.class);
    config = Booster.config();
    config.logger = Logger.NOOP;
    config.setUserProjectRootPath(project.toString());
    startServer();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
    Booster.reset();
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
  }

  /** What {@code boost start} does, with free ports and the temporary project directory. */
  @SuppressWarnings("deprecation")
  private void startServer() {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(true, "0"));
    config.setProvider(new LocalProvider(runtime, config.rockets, Provider::loadInfrastructurePackage));
    server = Infrastructure.infrastructure(config.rockets,
        LocalInfrastructure.Options.defaults().withRuntime(runtime).withShutdownHook(false)).startServer(config, 0);
    baseUrl = "http://localhost:" + server.port();
    int webSocketPort = URI.create(server.websocketURL()).getPort();
    LocalTestHelper helper = LocalTestHelper.build(config.appName,
        LocalTestHelper.Options.defaults().withBaseDirectory(project).withPorts(server.port(), webSocketPort));
    applicationUnderTest = new ApplicationTester(ProviderTestHelper.of(
        new ApplicationOutputs(helper.outputs().graphqlURL(), helper.outputs().websocketURL(),
            helper.outputs().healthURL()),
        new Counters() {
          @Override
          public long subscriptions() {
            return helper.counters().subscriptions();
          }

          @Override
          public long connections() {
            return helper.counters().connections();
          }

          @Override
          public long readModels(String readModelName) {
            return helper.counters().readModels(readModelName);
          }

          @Override
          public long events() {
            return helper.counters().events();
          }
        },
        new Queries() {
          @Override
          public List<Map<String, Object>> events(String primaryKey) {
            return helper.queries().events(primaryKey);
          }

          @Override
          public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
            return helper.queries().readModels(primaryKey, readModelName);
          }
        }));
    client = applicationUnderTest.graphql().client();
  }

  /**
   * What stopping {@code boost start} and starting it again does: the server and Booster stop, then the application
   * starts again (new configuration, new rocket registration) over the same project directory, on new free ports.
   */
  private void restart() {
    server.close();
    server = null;
    Booster.reset();
    Booster.start(App.class);
    config = Booster.config();
    config.logger = Logger.NOOP;
    config.setUserProjectRootPath(project.toString());
    startServer();
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> awaitReadModel(String document, String field, String id, String status) {
    GraphQLResponse[] response = new GraphQLResponse[1];
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() -> {
      response[0] = client.query(document, variables("id", id));
      Object readModel = response[0].get(field);
      return readModel instanceof Map<?, ?> map && (status == null || status.equals(map.get("status")));
    });
    return (Map<String, Object>) response[0].get(field);
  }

  private HttpResponse<byte[]> postJson(String path, String json, Map<String, String> extraHeaders) {
    Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
    headers.put("Content-Type", "application/json");
    return Http.send("POST", baseUrl + path, headers, json.getBytes(StandardCharsets.UTF_8));
  }

  private static Object json(HttpResponse<byte[]> response) {
    return BoosterJson.parse(Http.text(response));
  }

  private static Map<String, Object> failure(String title, String reason) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", title);
    body.put("reason", reason);
    return body;
  }

  @Nested
  class Orders {

    @Test
    void a_webhook_call_becomes_a_read_model_that_a_command_then_updates() {
      HttpResponse<byte[]> response = postJson("/orders",
          "{\"orderId\":\"order-1\",\"product\":\"keyboard\",\"quantity\":2}", Map.of("X-Source", "shop"));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(Http.text(response)).isEqualTo("{\"orderId\":\"order-1\",\"status\":\"accepted\"}");
      assertThat(Http.header(response, "Content-Type")).isEqualTo("application/json; charset=utf-8");
      assertThat(Http.header(response, "Access-Control-Allow-Origin")).isEqualTo("*");
      assertThat(Http.header(response, "Location")).isEqualTo("/orders/order-1");

      assertThat(awaitReadModel(ORDER, "OrderReadModel", "order-1", "placed")).isEqualTo(Map.of(
          "__typename", "OrderReadModel", "id", "order-1", "product", "keyboard", "quantity", 2, "status", "placed",
          "source", "shop"));
      List<Map<String, Object>> events = applicationUnderTest.query().events("Order-order-1-event");
      assertThat(events).singleElement().satisfies(event -> {
        assertThat(event).containsEntry("typeName", "OrderPlaced").containsEntry("entityTypeName", "Order");
        assertThat(event.get("requestID")).isInstanceOf(String.class);
        assertThat(event).doesNotContainKey("currentUser");
      });

      assertThat(client.mutate(CONFIRM, variables("orderId", "order-1")).get("ConfirmOrder")).isEqualTo(true);
      assertThat(awaitReadModel(ORDER, "OrderReadModel", "order-1", "confirmed")).containsEntry("status", "confirmed");
    }

    @Test
    void the_events_carry_the_user_of_the_token() {
      String token = applicationUnderTest.token().forUser("buyer@example.com", "User");

      postJson("/orders", "{\"orderId\":\"order-2\",\"product\":\"mouse\"}", Map.of("Authorization", "Bearer " + token));

      awaitReadModel(ORDER, "OrderReadModel", "order-2", "placed");
      Map<String, Object> event = applicationUnderTest.query().events("Order-order-2-event").getFirst();
      assertThat(((Map<?, ?>) event.get("currentUser")).get("username")).isEqualTo("buyer@example.com");
    }

    @Test
    void invalid_input_is_a_400_and_events_of_a_failing_handler_are_not_stored() {
      long before = applicationUnderTest.count().events();

      HttpResponse<byte[]> invalid = postJson("/orders", "{\"quantity\":1}", Map.of());
      HttpResponse<byte[]> rejected = postJson("/orders",
          "{\"orderId\":\"order-x\",\"product\":\"pen\",\"failAfterRegistering\":true}", Map.of());

      assertThat(invalid.statusCode()).isEqualTo(400);
      assertThat(json(invalid)).isEqualTo(failure("Invalid Parameter Error", "The product is required"));
      assertThat(Http.header(invalid, "Content-Type")).isEqualTo("application/json; charset=utf-8");
      assertThat(rejected.statusCode()).isEqualTo(400);
      assertThat(applicationUnderTest.query().events("Order-order-x-event")).isEmpty();
      assertThat(applicationUnderTest.count().events()).isEqualTo(before);
    }

    @Test
    void concurrent_webhook_calls_all_reach_their_read_models() throws Exception {
      List<Future<HttpResponse<byte[]>>> responses = new ArrayList<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < 20; i++) {
          String body = "{\"orderId\":\"bulk-" + i + "\",\"product\":\"item-" + i + "\",\"quantity\":" + i + "}";
          responses.add(executor.submit(() -> postJson("/orders", body, Map.of())));
        }
      }
      for (Future<HttpResponse<byte[]>> response : responses) {
        assertThat(response.get().statusCode()).isEqualTo(200);
      }
      for (int i = 0; i < 20; i++) {
        assertThat(awaitReadModel(ORDER, "OrderReadModel", "bulk-" + i, "placed")).containsEntry("quantity", i);
      }
    }
  }

  @Nested
  class Restarts {

    private int quantityOf(Map<String, Object> event) {
      return ((Number) ((Map<?, ?>) event.get("value")).get("quantity")).intValue();
    }

    @Test
    void webhook_events_and_read_models_survive_restarts_and_concurrent_calls_lose_no_event() throws Exception {
      assertThat(postJson("/orders", "{\"orderId\":\"durable-1\",\"product\":\"desk\",\"quantity\":1}", Map.of())
          .statusCode()).isEqualTo(200);
      awaitReadModel(ORDER, "OrderReadModel", "durable-1", "placed");

      restart();

      assertThat(server.recoveredEvents()).isEmpty();
      assertThat(awaitReadModel(ORDER, "OrderReadModel", "durable-1", "placed")).containsEntry("product", "desk")
          .containsEntry("quantity", 1);
      assertThat(applicationUnderTest.query().events("Order-durable-1-event")).hasSize(1);

      // 16 concurrent calls on the order placed before the restart and 16 on new orders
      List<Future<HttpResponse<byte[]>>> responses = new ArrayList<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 1; i <= 16; i++) {
          String same = "{\"orderId\":\"durable-1\",\"product\":\"desk\",\"quantity\":" + (i + 1) + "}";
          String other = "{\"orderId\":\"durable-other-" + i + "\",\"product\":\"chair\",\"quantity\":" + i + "}";
          responses.add(executor.submit(() -> postJson("/orders", same, Map.of())));
          responses.add(executor.submit(() -> postJson("/orders", other, Map.of())));
        }
      }
      for (Future<HttpResponse<byte[]>> response : responses) {
        assertThat(response.get().statusCode()).as(Http.text(response.get())).isEqualTo(200);
      }
      List<Map<String, Object>> events = applicationUnderTest.query().events("Order-durable-1-event");
      assertThat(events).hasSize(17);
      assertThat(events).extracting(event -> event.get("requestID")).doesNotHaveDuplicates();
      assertThat(events).extracting(this::quantityOf).containsExactlyInAnyOrder(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
      for (int i = 1; i <= 16; i++) {
        assertThat(awaitReadModel(ORDER, "OrderReadModel", "durable-other-" + i, "placed")).containsEntry("quantity", i);
      }
      // The entity, and so its read model, ends with the event stored last (events come newest first)
      int latest = quantityOf(events.getFirst());
      await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() ->
          client.query(ORDER, variables("id", "durable-1")).get("OrderReadModel") instanceof Map<?, ?> readModel
              && readModel.get("quantity") instanceof Number quantity && quantity.intValue() == latest);

      restart();

      assertThat(server.recoveredEvents()).isEmpty();
      assertThat(applicationUnderTest.query().events("Order-durable-1-event")).hasSize(17);
      assertThat(applicationUnderTest.count().events()).isEqualTo(1 + 16 + 16);
      assertThat(awaitReadModel(ORDER, "OrderReadModel", "durable-1", "placed")).containsEntry("quantity", latest);
      assertThat(postJson("/orders", "{\"orderId\":\"durable-2\",\"product\":\"lamp\"}", Map.of()).statusCode())
          .isEqualTo(200);
      assertThat(awaitReadModel(ORDER, "OrderReadModel", "durable-2", "placed")).containsEntry("product", "lamp");
      assertThat(client.mutate(CONFIRM, variables("orderId", "durable-1")).get("ConfirmOrder")).isEqualTo(true);
      assertThat(awaitReadModel(ORDER, "OrderReadModel", "durable-1", "confirmed")).containsEntry("quantity", latest);
    }
  }

  @Nested
  class Routing {

    @Test
    void a_webhook_named_graphql_is_shadowed_for_post_and_answers_get() {
      HttpResponse<byte[]> get = Http.get(baseUrl + "/graphql?name=webhook");

      assertThat(get.statusCode()).isEqualTo(200);
      assertThat(Http.text(get)).isEqualTo("Hello webhook");
      assertThat(client.query("query { __typename }").get("__typename")).isEqualTo("Query");
    }

    @Test
    void head_requests_reach_the_get_route_and_the_method_policy_answers_without_body() {
      HttpResponse<byte[]> head = Http.send("HEAD", baseUrl + "/status", Map.of(), null);

      // Express routes HEAD to the GET handler; dispatch answers `Method HEAD not allowed` and res.json sends no body
      assertThat(head.statusCode()).isEqualTo(500);
      assertThat(head.body()).isEmpty();
      assertThat(Http.header(head, "Content-Type")).isEqualTo("application/json; charset=utf-8");
    }

    @Test
    void a_host_header_jetty_accepts_but_the_url_parser_rejects_is_a_500_invalid_url() {
      String response = Http.raw(server.port(),
          "GET /status HTTP/1.1\r\nHost: a%20b\r\nConnection: close\r\n\r\n");
      String noHost = Http.raw(server.port(), "GET /status?name=x HTTP/1.0\r\n\r\n");

      assertThat(response).startsWith("HTTP/1.1 500 ")
          .endsWith("{\"title\":\"Illegal Argument Exception\",\"reason\":\"Invalid URL\"}");
      // An HTTP/1.0 request without Host resolves against http://undefined/
      assertThat(noHost).startsWith("HTTP/1.1 200 ").endsWith("Hello x");
    }

    @Test
    void octet_stream_bodies_over_the_100kb_of_express_raw_fail_before_the_webhook() {
      HttpResponse<byte[]> atLimit = Http.send("POST", baseUrl + "/inspect",
          Map.of("Content-Type", "application/octet-stream"), new byte[100 * 1024]);
      HttpResponse<byte[]> overLimit = Http.send("POST", baseUrl + "/inspect",
          Map.of("Content-Type", "application/octet-stream"), new byte[100 * 1024 + 1]);

      assertThat(atLimit.statusCode()).isEqualTo(200);
      assertThat(((Map<?, ?>) json(atLimit)).get("body")).isEqualTo(100 * 1024);
      // The PayloadTooLargeError reaches the server's error handler: httpStatusCodeFor gives 500
      assertThat(overLimit.statusCode()).isEqualTo(500);
      assertThat(json(overLimit)).isEqualTo(failure("Payload Too Large Error", "request entity too large"));
    }

    @Test
    void the_method_policy_and_the_exact_route_match_are_enforced_by_the_dispatcher() {
      HttpResponse<byte[]> get = Http.get(baseUrl + "/orders");
      HttpResponse<byte[]> upperCase = postJson("/Orders", "{}", Map.of());
      HttpResponse<byte[]> trailingSlash = postJson("/orders/", "{}", Map.of());

      assertThat(get.statusCode()).isEqualTo(500);
      assertThat(json(get)).isEqualTo(failure("Illegal State Exception", "Method GET not allowed"));
      assertThat(json(upperCase)).isEqualTo(failure("Illegal State Exception",
          "Could not find route Orders in parameters."));
      assertThat(json(trailingSlash)).isEqualTo(failure("Illegal State Exception",
          "Could not find route orders/ in parameters."));
    }

    @Test
    void other_methods_and_unknown_paths_get_the_express_404() {
      HttpResponse<byte[]> put = Http.send("PUT", baseUrl + "/orders", Map.of(), new byte[0]);
      HttpResponse<byte[]> unknown = Http.get(baseUrl + "/webhook/orders");

      assertThat(put.statusCode()).isEqualTo(404);
      assertThat(Http.text(put)).contains("Cannot PUT /orders");
      assertThat(unknown.statusCode()).isEqualTo(404);
    }

    @Test
    void preflights_are_answered_by_cors_and_graphql_keeps_working_next_to_the_webhooks() {
      HttpResponse<byte[]> preflight = Http.send("OPTIONS", baseUrl + "/orders",
          Map.of("Origin", "https://shop.example", "Access-Control-Request-Method", "POST"), null);

      assertThat(preflight.statusCode()).isEqualTo(204);
      assertThat(Http.header(preflight, "Access-Control-Allow-Origin")).isEqualTo("*");
      assertThat(client.query("query { __typename }").get("__typename")).isEqualTo("Query");
    }
  }

  @Nested
  class Requests {

    @SuppressWarnings("unchecked")
    private Map<String, Object> inspect(HttpResponse<byte[]> response) {
      assertThat(response.statusCode()).as(Http.text(response)).isEqualTo(200);
      return (Map<String, Object>) json(response);
    }

    @Test
    void a_get_request_has_the_qs_parsed_query_and_the_post_method_as_in_typescript() {
      Map<String, Object> seen = inspect(Http.get(baseUrl + "/inspect?a=1&list[]=x&list[]=y&nested[k]=v"));

      assertThat(seen).containsEntry("route", "inspect").containsEntry("method", "POST")
          .containsEntry("url", "/inspect?a=1&list[]=x&list[]=y&nested[k]=v")
          .containsEntry("originalUrl", "/inspect?a=1&list[]=x&list[]=y&nested[k]=v")
          .containsEntry("params", Map.of()).containsEntry("rawBodyType", "none").containsEntry("bodyType", "object")
          .containsEntry("body", Map.of()).containsEntry("multipart", false);
      assertThat(seen.get("query")).isEqualTo(Map.of("a", "1", "list", List.of("x", "y"), "nested", Map.of("k", "v")));
    }

    @Test
    void json_bodies_come_parsed_with_their_raw_bytes() {
      Map<String, Object> seen = inspect(postJson("/inspect", "{\"a\": [1, 2.5], \"b\": null}", Map.of()));

      assertThat(seen).containsEntry("rawBodyType", "byte[]").containsEntry("rawBody", "{\"a\": [1, 2.5], \"b\": null}")
          .containsEntry("bodyType", "object").containsEntry("contentType", "application/json");
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("a", List.of(1, 2.5));
      body.put("b", null);
      assertThat(seen.get("body")).isEqualTo(body);
    }

    @Test
    void forms_are_parsed_octet_streams_are_bytes_and_other_bodies_are_not_read() {
      Map<String, Object> form = inspect(Http.post(baseUrl + "/inspect", "application/x-www-form-urlencoded",
          "name=" + URLEncoder.encode("Ada Lovelace", StandardCharsets.UTF_8) + "&tags[]=a&tags[]=b"));
      Map<String, Object> binary = inspect(Http.send("POST", baseUrl + "/inspect",
          Map.of("Content-Type", "application/octet-stream"), new byte[] {0, 1, 2, 3, 4}));
      Map<String, Object> text = inspect(Http.post(baseUrl + "/inspect", "text/plain", "just text"));

      assertThat(form.get("body")).isEqualTo(Map.of("name", "Ada Lovelace", "tags", List.of("a", "b")));
      assertThat(form).containsEntry("rawBodyType", "none");
      assertThat(binary).containsEntry("bodyType", "byte[]").containsEntry("body", 5);
      assertThat(text).containsEntry("bodyType", "object").containsEntry("body", Map.of())
          .containsEntry("rawBodyType", "none");
    }

    @Test
    void malformed_json_is_rejected_by_the_server_parser_before_the_webhook() {
      HttpResponse<byte[]> response = postJson("/orders", "{not json", Map.of());

      assertThat(response.statusCode()).isEqualTo(500);
      assertThat(((Map<?, ?>) json(response)).get("title")).isEqualTo("Syntax Error");
    }
  }

  @Nested
  class Responses {

    @Test
    void a_text_response_with_a_custom_header() {
      HttpResponse<byte[]> response = Http.get(baseUrl + "/status?name=Ada");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(Http.text(response)).isEqualTo("Hello Ada");
      assertThat(Http.header(response, "Content-Type")).isEqualTo("text/plain; charset=utf-8");
      assertThat(Http.header(response, "X-Webhook")).isEqualTo("status");
    }

    @Test
    void a_file_download() {
      HttpResponse<byte[]> response = Http.get(baseUrl + "/reports/daily");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(Http.text(response)).isEqualTo(Webhooks.DailyReport.CSV);
      assertThat(Http.header(response, "Content-Type")).isEqualTo("text/csv");
      assertThat(Http.header(response, "Content-Disposition")).isEqualTo("attachment; filename=daily.csv");
    }

    @Test
    void headers_node_would_reject_fail_the_request_and_latin1_values_are_sent() {
      HttpResponse<byte[]> badName = Http.get(baseUrl + "/echo-header?name=Bad%20Header&value=x");
      HttpResponse<byte[]> badValue = Http.get(baseUrl + "/echo-header?name=X-Test&value=a%0D%0Ab");
      HttpResponse<byte[]> badFileName = Http.get(baseUrl + "/echo-header?file="
          + URLEncoder.encode("名.txt", StandardCharsets.UTF_8));
      HttpResponse<byte[]> latin1 = Http.get(baseUrl + "/echo-header?name=X-Test&value="
          + URLEncoder.encode("café", StandardCharsets.UTF_8));

      // res.setHeader throws inside handleWebhook: requestFailed answers 500 with the error (TypeError in TypeScript)
      assertThat(badName.statusCode()).isEqualTo(500);
      assertThat(json(badName)).isEqualTo(failure("Illegal Argument Exception",
          "Header name must be a valid HTTP token [\"Bad Header\"]"));
      assertThat(json(badValue)).isEqualTo(failure("Illegal Argument Exception",
          "Invalid character in header content [\"X-Test\"]"));
      assertThat(json(badFileName)).isEqualTo(failure("Illegal Argument Exception",
          "Invalid character in header content [\"Content-Disposition\"]"));
      assertThat(latin1.statusCode()).isEqualTo(200);
      assertThat(Http.header(latin1, "X-Test")).isEqualTo("café");
    }

    @Test
    void the_webhook_rocket_has_no_entry_in_the_rockets_health_indicator() {
      config.sensorConfiguration.health.booster.get("rockets").enabled = true;

      HttpResponse<byte[]> response = Http.get(baseUrl + "/sensor/health/rockets");

      // Its parameters are a list without rocketProviderPackage, so areRocketFunctionsUp finds no rocket
      assertThat(response.statusCode()).as(Http.text(response)).isEqualTo(200);
      assertThat((List<?>) json(response)).singleElement().satisfies(indicator -> {
        Map<?, ?> rockets = (Map<?, ?>) indicator;
        assertThat(rockets.get("id")).isEqualTo("rockets");
        assertThat(rockets.get("status")).isEqualTo("UNKNOWN");
        assertThat(rockets.get("details")).isEqualTo(Map.of("reason", "No Rockets found"));
      });
    }

    @Test
    void handler_errors_map_to_their_status() {
      HttpResponse<byte[]> boom = postJson("/fail", "{}", Map.of());
      HttpResponse<byte[]> notFound = postJson("/fail?kind=notfound", "{}", Map.of());

      assertThat(boom.statusCode()).isEqualTo(500);
      assertThat(json(boom)).isEqualTo(failure("Illegal State Exception", "boom"));
      assertThat(notFound.statusCode()).isEqualTo(404);
      assertThat(json(notFound)).isEqualTo(failure("Not Found Error", "Nothing here"));
    }
  }

  @Nested
  class Uploads {

    @Test
    void multipart_files_become_documents_with_their_exact_bytes() {
      byte[] picture = new byte[100_000];
      new Random(7).nextBytes(picture);
      byte[] notes = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);
      byte[] body = MultipartBody.create().field("title", "Quarterly report")
          .file("picture", "chart.png", "image/png", picture)
          .file("notes", "notes.txt", "text/plain", notes)
          .build();

      HttpResponse<byte[]> response = Http.send("POST", baseUrl + "/upload",
          Map.of("Content-Type", MultipartBody.CONTENT_TYPE), body);

      assertThat(response.statusCode()).as(Http.text(response)).isEqualTo(200);
      List<?> documents = (List<?>) ((Map<?, ?>) json(response)).get("documents");
      assertThat(documents).hasSize(2);
      Map<?, ?> first = (Map<?, ?>) documents.getFirst();
      assertThat(first.get("fileName")).isEqualTo("chart.png");
      assertThat(first.get("size")).isEqualTo(100_000);
      assertThat(first.get("sha256")).isEqualTo(Webhooks.sha256(picture));
      Map<String, Object> readModel = awaitReadModel(DOCUMENT, "DocumentReadModel", (String) first.get("id"), null);
      assertThat(readModel).containsEntry("title", "Quarterly report").containsEntry("mimeType", "image/png")
          .containsEntry("size", 100_000).containsEntry("sha256", Webhooks.sha256(picture));
      Map<?, ?> second = (Map<?, ?>) documents.get(1);
      assertThat(awaitReadModel(DOCUMENT, "DocumentReadModel", (String) second.get("id"), null))
          .containsEntry("fileName", "notes.txt").containsEntry("sha256", Webhooks.sha256(notes));
    }

    @Test
    void files_over_the_limit_are_truncated_and_broken_forms_fail() {
      byte[] big = new byte[(int) Webhooks.UPLOAD_LIMIT + 1000];
      new Random(8).nextBytes(big);
      byte[] body = MultipartBody.create().file("big", "big.bin", "application/octet-stream", big).build();

      HttpResponse<byte[]> truncated = Http.send("POST", baseUrl + "/upload",
          Map.of("Content-Type", MultipartBody.CONTENT_TYPE), body);
      HttpResponse<byte[]> broken = Http.send("POST", baseUrl + "/upload",
          Map.of("Content-Type", MultipartBody.CONTENT_TYPE), MultipartBody.create().field("a", "b").buildTruncated());
      HttpResponse<byte[]> notMultipart = postJson("/upload", "{}", Map.of());

      Map<?, ?> document = (Map<?, ?>) ((List<?>) ((Map<?, ?>) json(truncated)).get("documents")).getFirst();
      assertThat(document.get("size")).isEqualTo((int) Webhooks.UPLOAD_LIMIT);
      assertThat(broken.statusCode()).isEqualTo(500);
      assertThat(json(broken)).isEqualTo(failure("Illegal State Exception", "Unexpected end of form"));
      assertThat(json(notMultipart)).isEqualTo(failure("Invalid Parameter Error",
          "A multipart/form-data body is required"));
    }
  }

  @Nested
  class Authorization {

    @Test
    void role_protected_webhooks_need_a_token_with_the_role() {
      String admin = applicationUnderTest.token().forUser("admin@example.com", "Admin");
      String user = applicationUnderTest.token().forUser("user@example.com", "User");

      HttpResponse<byte[]> anonymous = postJson("/admin/ping", "{}", Map.of());
      HttpResponse<byte[]> wrongRole = postJson("/admin/ping", "{}", Map.of("Authorization", "Bearer " + user));
      HttpResponse<byte[]> allowed = postJson("/admin/ping", "{}", Map.of("Authorization", "Bearer " + admin));

      assertThat(anonymous.statusCode()).isEqualTo(401);
      assertThat(json(anonymous)).isEqualTo(failure("Not Authorized Error", "Access denied for this resource"));
      assertThat(wrongRole.statusCode()).isEqualTo(401);
      assertThat(allowed.statusCode()).isEqualTo(200);
      assertThat(allowed.body()).isEmpty();
      assertThat(Http.header(allowed, "Content-Type")).isEqualTo("text/plain; charset=utf-8");
    }

    @Test
    void tokens_are_verified_on_public_webhooks_too() {
      String expired = applicationUnderTest.token().forUser("user@example.com", "User",
          TokenOptions.builder().expiresIn(0).build());
      String valid = applicationUnderTest.token().forUser("user@example.com", "User");

      HttpResponse<byte[]> garbage = Http.send("GET", baseUrl + "/status", Map.of("Authorization", "Bearer garbage"),
          null);
      HttpResponse<byte[]> expiredResponse = Http.send("GET", baseUrl + "/status",
          Map.of("Authorization", "Bearer " + expired), null);
      HttpResponse<byte[]> validResponse = Http.send("GET", baseUrl + "/inspect",
          Map.of("Authorization", "Bearer " + valid), null);

      assertThat(garbage.statusCode()).isEqualTo(401);
      assertThat(((Map<?, ?>) json(garbage)).get("title")).isEqualTo("Not Authorized Error");
      assertThat(expiredResponse.statusCode()).isEqualTo(401);
      assertThat(((Map<?, ?>) json(expiredResponse)).get("title")).isEqualTo("Booster Token Expired Error");
      assertThat(((Map<?, ?>) json(validResponse)).get("user")).isEqualTo("user@example.com");
    }

    @Test
    void a_custom_authorizer_reads_the_request() {
      HttpResponse<byte[]> denied = postJson("/partner/notify", "{}", Map.of("X-Api-Key", "wrong"));
      HttpResponse<byte[]> allowed = postJson("/partner/notify", "{}", Map.of("X-Api-Key", Webhooks.API_KEY));

      assertThat(denied.statusCode()).isEqualTo(401);
      assertThat(json(denied)).isEqualTo(failure("Not Authorized Error", "Invalid API key"));
      assertThat(allowed.statusCode()).isEqualTo(200);
      assertThat(Http.text(allowed)).isEqualTo("ok");
    }
  }
}
