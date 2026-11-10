package com.boostercloud.rocket.webhook.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.TokenHelper;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.provider.azure.AzureClients;
import com.boostercloud.framework.provider.azure.AzureProviderLibrary;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosConnectionString;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.webhook.e2e.app.App;
import com.boostercloud.rocket.webhook.e2e.app.Webhooks;
import com.boostercloud.rocket.webhook.support.FakeCosmosServer;
import com.boostercloud.rocket.webhook.support.MultipartBody;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Azure runtime path of a webhook call, in process and without Azure: the Java entry point the rocket's
 * {@code function.json} files point at ({@code BoosterAzureFunctions.rocketHttp}) receives a Functions HTTP request,
 * {@code boosterRocketDispatcher} finds the rocket function through the {@code BOOSTER_ROCKET_FUNCTION_ID} app setting
 * (the Azure provider's rocket adapter), the handler runs and its events are stored in Cosmos DB through the provider's
 * REST client, here against an in-JVM fake Cosmos DB that records the requests.
 */
class AzureRuntimeContractTest {

  private static final String HOST = "https://webhooke2eproductionrgwebhookfunc.azurewebsites.net";

  /** A Functions HTTP request with the body bound as bytes. */
  static final class FakeRequest implements HttpRequestMessage<Optional<byte[]>> {
    private final HttpMethod method;
    private final URI uri;
    private final Map<String, String> headers;
    private final byte[] body;

    FakeRequest(HttpMethod method, String url, Map<String, String> headers, byte[] body) {
      this.method = method;
      this.uri = URI.create(url);
      this.headers = headers;
      this.body = body;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public HttpMethod getHttpMethod() {
      return method;
    }

    @Override
    public Map<String, String> getHeaders() {
      return headers;
    }

    @Override
    public Map<String, String> getQueryParameters() {
      return Map.of();
    }

    @Override
    public Optional<byte[]> getBody() {
      return Optional.ofNullable(body);
    }

    @Override
    public HttpResponseMessage.Builder createResponseBuilder(HttpStatus status) {
      return new FakeResponse(status);
    }

    @Override
    public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
      return new FakeResponse(status);
    }
  }

  /** What the entry point answers the worker. */
  static final class FakeResponse implements HttpResponseMessage, HttpResponseMessage.Builder {
    private HttpStatusType status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Object body;

    FakeResponse(HttpStatusType status) {
      this.status = status;
    }

    @Override
    public HttpResponseMessage.Builder status(HttpStatusType newStatus) {
      this.status = newStatus;
      return this;
    }

    @Override
    public HttpResponseMessage.Builder header(String key, String value) {
      headers.put(key, value);
      return this;
    }

    @Override
    public HttpResponseMessage.Builder body(Object newBody) {
      this.body = newBody;
      return this;
    }

    @Override
    public HttpResponseMessage build() {
      return this;
    }

    @Override
    public HttpStatusType getStatus() {
      return status;
    }

    @Override
    public String getHeader(String key) {
      return headers.get(key);
    }

    @Override
    public Object getBody() {
      return body;
    }

    Map<String, String> headers() {
      return headers;
    }
  }

  private FakeCosmosServer cosmos;
  private BoosterConfig config;
  private final BoosterAzureFunctions functions = new BoosterAzureFunctions();

  @BeforeEach
  void setUp() {
    cosmos = new FakeCosmosServer();
    System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, "production");
    Booster.start(App.class);
    config = Booster.config();
    config.logger = Logger.NOOP;
    AtomicInteger ids = new AtomicInteger();
    CosmosRestClient client = new CosmosRestClient(CosmosConnectionString.parse(cosmos.connectionString()),
        CosmosRetryOptions.defaults(), HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        Clock.systemUTC(), millis -> { }, () -> "document-" + ids.incrementAndGet());
    setProvider(new AzureProviderLibrary(config.rockets, AzureClients.of(client, null, null, null)));
    EnvironmentVariables.set("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook");
  }

  @SuppressWarnings("deprecation")
  private void setProvider(AzureProviderLibrary provider) {
    config.setProvider(provider);
  }

  @AfterEach
  void tearDown() {
    cosmos.close();
    Booster.reset();
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    EnvironmentVariables.clearOverrides();
  }

  private FakeResponse call(HttpMethod method, String path, Map<String, String> headers, byte[] body) {
    return (FakeResponse) functions.rocketHttp(new FakeRequest(method, HOST + path, headers, body), null);
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String text(FakeResponse response) {
    return response.getBody() instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8)
        : (String) response.getBody();
  }

  /**
   * The master-key token of the Cosmos DB REST API, derived here from Microsoft's documentation ("Access control in the
   * Azure Cosmos DB SQL API": {@code verb + "\n" + resourceType + "\n" + resourceLink + "\n" + date + "\n" + "" +
   * "\n"}, verb, type and date lower-cased, HMAC-SHA256 with the base64-decoded key, then
   * {@code encodeURIComponent("type=master&ver=1.0&sig=" + base64(signature))}), not with the provider's code, and
   * compared with what the provider sent.
   */
  private static void assertSignedWithTheMasterKey(FakeCosmosServer.RecordedRequest request, String verb,
      String resourceType, String resourceLink) {
    String date = request.header("x-ms-date");
    assertThat(date).as("x-ms-date").isNotNull();
    Instant sent = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    assertThat(Duration.between(sent, Instant.now()).abs()).isLessThan(Duration.ofMinutes(5));
    assertThat(request.header("x-ms-version")).as("x-ms-version").isNotBlank();
    String stringToSign = verb + "\n" + resourceType + "\n" + resourceLink + "\n" + date.toLowerCase(Locale.ROOT)
        + "\n" + "" + "\n";
    try {
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(Base64.getDecoder().decode(FakeCosmosServer.KEY), "HmacSHA256"));
      String signature = Base64.getEncoder().encodeToString(hmac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
      // encodeURIComponent and URLEncoder agree on the base64 alphabet and on '=' and '&'
      assertThat(request.header("authorization"))
          .isEqualTo(URLEncoder.encode("type=master&ver=1.0&sig=" + signature, StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> storedEvents() {
    return cosmos.requests().stream()
        .filter(request -> "True".equals(request.header("x-ms-cosmos-is-batch-request")))
        .flatMap(request -> ((List<Map<String, Object>>) request.json()).stream())
        .map(operation -> (Map<String, Object>) operation.get("resourceBody"))
        .toList();
  }

  @Test
  void a_webhook_call_stores_its_events_in_cosmos_and_answers_the_handler_result() {
    String token = new TokenHelper().forUser("buyer@example.com", "User");

    FakeResponse response = call(HttpMethod.POST, "/api/orders?via=azure", Map.of("Content-Type", "application/json",
        "Authorization", "Bearer " + token, "X-Source", "partner"),
        utf8("{\"orderId\":\"order-9\",\"product\":\"monitor\",\"quantity\":3}"));

    assertThat(response.getStatus().value()).isEqualTo(200);
    assertThat(text(response)).isEqualTo("{\"orderId\":\"order-9\",\"status\":\"accepted\"}");
    assertThat(response.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
        Map.entry("Location", "/orders/order-9"), Map.entry("Content-Type", "application/json"));
    assertThat(cosmos.requests()).singleElement().satisfies(request -> {
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.path()).isEqualTo("/dbs/webhook-e2e-app/colls/webhook-e2e-app-events-store/docs");
      assertThat(request.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Order-order-9-event\"]");
      assertSignedWithTheMasterKey(request, "post", "docs", "dbs/webhook-e2e-app/colls/webhook-e2e-app-events-store");
    });
    assertThat(storedEvents()).singleElement().satisfies(event -> {
      assertThat(event).containsEntry("kind", "event").containsEntry("superKind", "domain")
          .containsEntry("entityTypeName", "Order").containsEntry("entityID", "order-9")
          .containsEntry("typeName", "OrderPlaced")
          .containsEntry("entityTypeName_entityID_kind", "Order-order-9-event");
      assertThat(event.get("value")).isEqualTo(Map.of("orderId", "order-9", "product", "monitor", "quantity", 3,
          "source", "partner"));
      assertThat(((Map<?, ?>) event.get("currentUser")).get("username")).isEqualTo("buyer@example.com");
      assertThat(event.get("requestID")).isInstanceOf(String.class);
    });
  }

  @Test
  void multipart_uploads_reach_the_handler_byte_for_byte() {
    byte[] picture = new byte[50_000];
    new Random(3).nextBytes(picture);
    byte[] body = MultipartBody.create().field("title", "Scan")
        .file("scan", "scan.bin", "application/octet-stream", picture).build();

    FakeResponse response = call(HttpMethod.POST, "/api/upload", Map.of("Content-Type", MultipartBody.CONTENT_TYPE),
        body);

    assertThat(response.getStatus().value()).as(text(response)).isEqualTo(200);
    Map<?, ?> document = (Map<?, ?>) ((List<?>) ((Map<?, ?>) BoosterJson.parse(text(response))).get("documents"))
        .getFirst();
    assertThat(document.get("size")).isEqualTo(50_000);
    assertThat(document.get("sha256")).isEqualTo(Webhooks.sha256(picture));
    assertThat(storedEvents()).singleElement().satisfies(event -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = (Map<String, Object>) event.get("value");
      assertThat(value).containsEntry("title", "Scan").containsEntry("sha256", Webhooks.sha256(picture));
    });
    assertThat(cosmos.requests()).singleElement().satisfies(request -> assertSignedWithTheMasterKey(request, "post",
        "docs", "dbs/webhook-e2e-app/colls/webhook-e2e-app-events-store"));
  }

  @Test
  void the_query_comes_from_the_url_and_failures_carry_their_headers_on_azure() {
    FakeResponse inspected = call(HttpMethod.GET, "/api/inspect?a=1&a=2&b=x+y", Map.of(), null);
    FakeResponse notAllowed = call(HttpMethod.GET, "/api/orders", Map.of(), null);

    Map<?, ?> seen = (Map<?, ?>) BoosterJson.parse(text(inspected));
    assertThat(seen.get("query")).isEqualTo(Map.of("a", "2", "b", "x y"));
    assertThat(seen.get("url")).isEqualTo(HOST + "/api/inspect?a=1&a=2&b=x+y");
    assertThat(seen.get("rawBodyType")).isEqualTo("String");
    assertThat(seen.get("body")).isEqualTo("");
    assertThat(notAllowed.getStatus().value()).isEqualTo(500);
    assertThat(text(notAllowed)).isEqualTo("{\"title\":\"Illegal State Exception\",\"reason\":\"Method GET not allowed\"}");
    assertThat(notAllowed.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
        Map.entry("Content-Type", "application/json"));
    assertThat(cosmos.requests()).isEmpty();
  }

  @Test
  void text_and_file_results_and_the_authorization_errors() {
    FakeResponse status = call(HttpMethod.GET, "/api/status?name=Grace", Map.of(), null);
    FakeResponse report = call(HttpMethod.GET, "/api/reports/daily", Map.of(), null);
    FakeResponse denied = call(HttpMethod.POST, "/api/admin/ping", Map.of(), utf8("{}"));
    FakeResponse unknown = call(HttpMethod.POST, "/api/nope", Map.of(), utf8("{}"));

    assertThat(text(status)).isEqualTo("Hello Grace");
    assertThat(status.headers()).containsEntry("X-Webhook", "status").containsEntry("Content-Type", "text/plain");
    assertThat(report.getBody()).isEqualTo(utf8(Webhooks.DailyReport.CSV));
    assertThat(report.headers()).containsEntry("Content-Disposition", "attachment; filename=daily.csv");
    assertThat(denied.getStatus().value()).isEqualTo(401);
    assertThat(text(denied)).isEqualTo("{\"title\":\"Not Authorized Error\",\"reason\":\"Access denied for this resource\"}");
    assertThat(text(unknown)).contains("Could not find route nope in parameters.");
  }

  @Test
  void without_the_rocket_id_app_setting_the_dispatcher_fails_and_the_functions_host_answers_500() {
    EnvironmentVariables.unset("BOOSTER_ROCKET_FUNCTION_ID");

    assertThatThrownBy(() -> call(HttpMethod.POST, "/api/orders", Map.of(), utf8("{}")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Attempt to execute a rocket function but the ID is missing.");
  }
}
