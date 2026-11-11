package com.boostercloud.framework.provider.azure.eventhubs.rest;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.eventhubs.CreateBatchOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventData;
import com.boostercloud.framework.provider.azure.eventhubs.EventDataBatch;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubsException;
import com.boostercloud.framework.provider.azure.eventhubs.RetryMode;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.RecordedRequest;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.Response;
import com.boostercloud.framework.provider.azure.support.Fixtures;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests of the Event Hubs REST producer: the "Send batch events" request (path, query, content type,
 * shared access signature recomputed independently, body), the batch size limit and the retry policy.
 */
class EventHubsRestProducerClientContractTest {

  static final String KEY_NAME = "RootManageSharedAccessKey";
  static final String KEY = "xk3XW8kE9KqbRlXf0eAbGvL0gK3E7Z9cN1rT2y4u6wQ=";
  static final Instant NOW = Instant.parse("2026-11-10T09:15:04Z");

  private FakeAzureServer server;
  private final List<Long> sleeps = new ArrayList<>();

  @BeforeEach
  void start() {
    server = new FakeAzureServer();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String connectionString() {
    return "Endpoint=" + server.baseUrl() + "/;SharedAccessKeyName=" + KEY_NAME + ";SharedAccessKey=" + KEY
        + ";EntityPath=booster_events";
  }

  private EventHubsRestProducerClient producer(EventHubRetryOptions options) {
    return new EventHubsRestProducerClient(EventHubConnectionString.parse(connectionString()), "booster_events",
        options, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), Clock.fixed(NOW, ZoneOffset.UTC),
        sleeps::add, () -> 0.5);
  }

  static String expectedToken(String resourceUri, long expiry) throws Exception {
    String audience = URLEncoder.encode(resourceUri, StandardCharsets.UTF_8);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature = Base64.getEncoder()
        .encodeToString(mac.doFinal((audience + "\n" + expiry).getBytes(StandardCharsets.UTF_8)));
    return "SharedAccessSignature sr=" + audience + "&sig=" + URLEncoder.encode(signature, StandardCharsets.UTF_8)
        + "&se=" + expiry + "&skn=" + KEY_NAME;
  }

  @Test
  void sendsTheBatchToTheMessagesEndpointWithASasTokenAndPartitionKeys() throws Exception {
    server.enqueue(Response.empty(201));
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));
    Map<String, Object> event = obj("id", "1", "_etag", "\"e1\"", "value", obj("n", 1));

    EventDataBatch batch = producer.createBatch(CreateBatchOptions.withPartitionKey("Cart-42-event"));
    assertThat(batch.tryAdd(new EventData(event))).isTrue();
    assertThat(batch.tryAdd(new EventData("already text"))).isTrue();
    producer.sendBatch(batch);

    RecordedRequest request = server.request(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/booster_events/messages");
    assertThat(request.query()).isEqualTo("timeout=60&api-version=2014-01");
    assertThat(request.header("content-type")).isEqualTo("application/vnd.microsoft.servicebus.json");
    String resourceUri = server.baseUrl() + "/booster_events";
    assertThat(request.header("authorization")).isEqualTo(expectedToken(resourceUri, NOW.getEpochSecond() + 3600));
    assertThat(request.json()).isEqualTo(List.of(
        obj("Body", "{\"id\":\"1\",\"_etag\":\"\\\"e1\\\"\",\"value\":{\"n\":1}}",
            "BrokerProperties", obj("PartitionKey", "Cart-42-event")),
        obj("Body", "already text", "BrokerProperties", obj("PartitionKey", "Cart-42-event"))));
    assertThat(batch.sizeInBytes()).isEqualTo(request.body().getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void theSasTokenFieldsMatchTheServiceBusFormat() {
    String token = SharedAccessSignatures.token("https://ns.servicebus.windows.net/booster_events", KEY_NAME, KEY, 1700);
    Map<String, String> fields = new LinkedHashMap<>();
    for (String part : token.substring("SharedAccessSignature ".length()).split("&")) {
      String[] kv = part.split("=", 2);
      fields.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    assertThat(fields.keySet()).containsExactly("sr", "sig", "se", "skn");
    assertThat(fields).containsEntry("sr", "https://ns.servicebus.windows.net/booster_events")
        .containsEntry("se", "1700").containsEntry("skn", KEY_NAME);
    assertThat(fields.get("sig")).isEqualTo(SharedAccessSignatures.sign(
        "https%3A%2F%2Fns.servicebus.windows.net%2Fbooster_events\n1700", KEY));
  }

  @Test
  void tryAddRefusesEventsThatWouldExceedTheMaximumSize() {
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(0, RetryMode.FIXED));
    EventDataBatch batch = producer.createBatch(new CreateBatchOptions("pk", 120L));

    assertThat(batch.tryAdd(new EventData(obj("a", "x".repeat(20))))).isTrue();
    assertThat(batch.tryAdd(new EventData(obj("a", "x".repeat(60))))).isFalse();
    assertThat(batch.count()).isEqualTo(1);
    assertThat(batch.sizeInBytes()).isLessThanOrEqualTo(120);
    assertThat(producer.createBatch(CreateBatchOptions.withPartitionKey("pk")).maxSizeInBytes()).isEqualTo(262_144);
  }

  @Test
  void theDefaultBatchStaysWithinTheBasicTierPublicationLimitTheInfrastructureDeploys() {
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(0, RetryMode.FIXED));
    EventDataBatch batch = producer.createBatch(CreateBatchOptions.withPartitionKey("Product-1-event"));

    int accepted = 0;
    for (int i = 0; i < 100 && batch.tryAdd(new EventData(obj("value", "x".repeat(3000)))); i++) {
      accepted++;
    }

    // Basic namespaces (TerraformEventHubNamespace) accept publications of up to 256 KB; 100 events of 3 KB exceed it
    assertThat(accepted).isBetween(1, 99);
    assertThat(batch.sizeInBytes()).isLessThanOrEqualTo(256 * 1024);
  }

  @Test
  void retriesServerBusyWithExponentialDelays() {
    server.enqueue(Response.json(503, Fixtures.read("eventhubs/error-503.json")))
        .enqueue(Response.json(503, Fixtures.read("eventhubs/error-503.json")))
        .enqueue(Response.empty(201));
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));
    EventDataBatch batch = producer.createBatch(CreateBatchOptions.withPartitionKey("pk"));
    batch.tryAdd(new EventData("e"));

    producer.sendBatch(batch);

    // boundedRandDelta = 30000 * 0.8 + floor(0.5 * (36000 - 24000)) = 30000; (2^1 - 1) * 30000, (2^2 - 1) * 30000 capped
    assertThat(sleeps).containsExactly(30_000L, 90_000L);
    assertThat(server.requests()).hasSize(3);
  }

  @Test
  void fixedModeWaitsTheBaseDelayAndGivesUpAfterMaxRetries() {
    server.enqueue(Response.json(503, Fixtures.read("eventhubs/error-503.json")))
        .enqueue(Response.json(503, Fixtures.read("eventhubs/error-503.json")));
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(1, RetryMode.FIXED));
    EventDataBatch batch = producer.createBatch(CreateBatchOptions.withPartitionKey("pk"));
    batch.tryAdd(new EventData("e"));

    assertThatThrownBy(() -> producer.sendBatch(batch))
        .isInstanceOfSatisfying(EventHubsException.class, error -> {
          assertThat(error.statusCode()).isEqualTo(503);
          assertThat(error.getMessage()).contains("The server is busy");
        });
    assertThat(sleeps).containsExactly(30_000L);
  }

  @Test
  void authenticationFailuresAreNotRetried() {
    server.enqueue(Response.json(401, Fixtures.read("eventhubs/error-401.json")));
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));
    EventDataBatch batch = producer.createBatch(CreateBatchOptions.withPartitionKey("pk"));
    batch.tryAdd(new EventData("e"));

    assertThatThrownBy(() -> producer.sendBatch(batch))
        .isInstanceOfSatisfying(EventHubsException.class, error -> assertThat(error.isTransient()).isFalse());
    assertThat(sleeps).isEmpty();
  }

  @Test
  void anEmptyBatchIsNotSent() {
    EventHubsRestProducerClient producer = producer(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));

    producer.sendBatch(producer.createBatch(CreateBatchOptions.withPartitionKey("pk")));

    assertThat(server.requests()).isEmpty();
  }

  @Test
  void theEntityPathMustMatchTheEventHubName() {
    EventHubConnectionString parsed = EventHubConnectionString.parse(connectionString());

    assertThatThrownBy(() -> new EventHubsRestProducerClient(parsed, "other", EventHubRetryOptions.of(1, RetryMode.FIXED),
        HttpClient.newHttpClient(), Clock.systemUTC(), sleeps::add, Math::random))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The entity path \"booster_events\" in connectionString doesn't match with eventHubName: \"other\".");
    assertThat(parsed.toString()).doesNotContain(KEY);
    assertThat(EventHubConnectionString.parse("Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=n;"
        + "SharedAccessKey=k").httpBase().toString()).isEqualTo("https://ns.servicebus.windows.net/");
  }

  @Test
  void readsTheRetryOptionsLikeIndexTs() {
    assertThat(EventHubRetryOptions.fromSettings(Map.<String, String>of()::get))
        .isEqualTo(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));
    assertThat(EventHubRetryOptions.fromSettings(Map.of("EVENTHUB_MAX_RETRIES", "3", "EVENTHUB_MODE", "fixed")::get))
        .isEqualTo(EventHubRetryOptions.of(3, RetryMode.FIXED));
    // Quirk Q5: the mode is only honoured when EVENTHUB_MAX_RETRIES is set
    assertThat(EventHubRetryOptions.fromSettings(Map.of("EVENTHUB_MODE", "FIXED")::get))
        .isEqualTo(EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL));
  }
}
