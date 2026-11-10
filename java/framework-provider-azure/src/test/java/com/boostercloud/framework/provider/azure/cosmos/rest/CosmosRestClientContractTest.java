package com.boostercloud.framework.provider.azure.cosmos.rest;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.cosmos.BatchResponse;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.OperationInput;
import com.boostercloud.framework.provider.azure.cosmos.PartitionKeyValue;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.RecordedRequest;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.Response;
import com.boostercloud.framework.provider.azure.support.Fixtures;
import com.boostercloud.framework.types.BoosterJson;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests of the Cosmos DB REST client: the production code runs against an in-JVM HTTP server and every
 * request is checked against the REST API contract (method, path, headers, master-key signature recomputed here
 * independently, query bodies, continuation handling, retries), and recorded service responses are parsed.
 */
class CosmosRestClientContractTest {

  /** The well-known (public) key of the Cosmos DB emulator. */
  static final String KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
  static final Instant NOW = Instant.parse("2026-11-10T09:15:04Z");
  static final String DATE = "Tue, 10 Nov 2026 09:15:04 GMT";
  static final String DB = "new-booster-app-app";
  static final String EVENTS = "new-booster-app-app-events-store";
  static final String COLL_LINK = "dbs/" + DB + "/colls/" + EVENTS;

  private FakeAzureServer server;
  private final List<Long> sleeps = new ArrayList<>();
  private final AtomicInteger ids = new AtomicInteger();

  @BeforeEach
  void start() {
    server = new FakeAzureServer();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private CosmosRestClient client() {
    return client(CosmosRetryOptions.defaults());
  }

  private CosmosRestClient client(CosmosRetryOptions options) {
    return new CosmosRestClient(
        CosmosConnectionString.parse("AccountEndpoint=" + server.baseUrl() + "/;AccountKey=" + KEY + ";"),
        options,
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        sleeps::add,
        () -> "generated-" + ids.incrementAndGet());
  }

  private CosmosContainer events(CosmosRestClient client) {
    return client.database(DB).container(EVENTS);
  }

  private static Response container(String id, String partitionKeyPath) {
    return Response.json(200, Fixtures.read("cosmos/container.json")
        .replace("{{containerId}}", id).replace("{{partitionKeyPath}}", partitionKeyPath));
  }

  /** The master-key signature, computed independently from the production code. */
  static String expectedAuthorization(String verb, String resourceType, String resourceLink) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
    String payload = verb.toLowerCase() + "\n" + resourceType + "\n" + resourceLink + "\n" + DATE.toLowerCase() + "\n\n";
    String signature = Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    return URLEncoder.encode("type=master&ver=1.0&sig=" + signature, StandardCharsets.UTF_8);
  }

  private static void assertSigned(RecordedRequest request, String verb, String type, String link) throws Exception {
    assertThat(request.method()).isEqualTo(verb);
    assertThat(request.header("x-ms-date")).isEqualTo(DATE);
    assertThat(request.header("x-ms-version")).isEqualTo("2020-07-15");
    assertThat(request.header("authorization")).isEqualTo(expectedAuthorization(verb, type, link));
  }

  @Nested
  class Authorization {

    @Test
    void formatsXmsDateAsRfc1123WithATwoDigitDay() {
      assertThat(CosmosAuthorization.formatDate(Instant.parse("2026-11-03T08:05:01Z")))
          .isEqualTo("Tue, 03 Nov 2026 08:05:01 GMT");
    }

    @Test
    void buildsTheStringToSignWithLowerCasedVerbTypeAndDateAndTheCaseSensitiveLink() {
      assertThat(CosmosAuthorization.stringToSign("GET", "DOCS", "dbs/MyDb/colls/MyColl/docs/Id", DATE))
          .isEqualTo("get\ndocs\ndbs/MyDb/colls/MyColl/docs/Id\ntue, 10 nov 2026 09:15:04 gmt\n\n");
    }

    @Test
    void signsLikeTheReferenceImplementation() throws Exception {
      assertThat(CosmosAuthorization.authorizationHeader("POST", "docs", COLL_LINK, DATE, KEY))
          .isEqualTo(expectedAuthorization("POST", "docs", COLL_LINK));
    }

    @Test
    void parsesConnectionStringsAndRejectsIncompleteOnes() {
      CosmosConnectionString parsed = CosmosConnectionString.parse(
          "AccountEndpoint=https://acc.documents.azure.com:443/;AccountKey=" + KEY + ";");
      assertThat(parsed.endpoint().toString()).isEqualTo("https://acc.documents.azure.com:443/");
      assertThat(parsed.accountKey()).isEqualTo(KEY);
      assertThat(parsed.toString()).doesNotContain(KEY);
      assertThatThrownBy(() -> CosmosConnectionString.parse(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Could not parse the provided connection string");
    }
  }

  @Nested
  class PointOperations {

    @Test
    void createReadsThePartitionKeyDefinitionOnceAndGeneratesAnId() throws Exception {
      server.enqueue(container(EVENTS, "/entityTypeName_entityID_kind"))
          .enqueue(Fixtures.response("cosmos/event-document.json", 201).withHeader("etag", "\"e1\""))
          .enqueue(Fixtures.response("cosmos/event-document.json", 201));
      CosmosRestClient client = client();

      ItemResponse response = events(client).items()
          .create(obj("kind", "event", "entityTypeName_entityID_kind", "Cart-42-event", "createdAt", "t"));
      events(client).items().create(obj("entityTypeName_entityID_kind", "Cart-43-event", "id", "given"));

      assertThat(server.requests()).hasSize(3);
      RecordedRequest definition = server.request(0);
      assertSigned(definition, "GET", "colls", COLL_LINK);
      assertThat(definition.path()).isEqualTo("/" + COLL_LINK);

      RecordedRequest create = server.request(1);
      assertSigned(create, "POST", "docs", COLL_LINK);
      assertThat(create.path()).isEqualTo("/" + COLL_LINK + "/docs");
      assertThat(create.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
      assertThat(create.header("content-type")).isEqualTo("application/json");
      assertThat(create.json())
          .isEqualTo(obj("kind", "event", "entityTypeName_entityID_kind", "Cart-42-event", "createdAt", "t",
              "id", "generated-1"));
      assertThat(server.request(2).json()).isEqualTo(obj("entityTypeName_entityID_kind", "Cart-43-event", "id", "given"));
      assertThat(server.request(2).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-43-event\"]");

      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(response.etag()).isEqualTo("\"e1\"");
      assertThat(response.resource()).containsEntry("_etag", "\"0800f1c2-0000-0d00-0000-65a000000000\"");
    }

    @Test
    void aDocumentWithoutThePartitionKeyPropertyUsesTheUndefinedPartitionKey() {
      server.enqueue(container("dedup", "/primaryKey")).enqueue(Response.json(201, "{}"));

      client().database(DB).container("dedup").items().create(obj("createdAt", "t", "ttl", 86400));

      assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[{}]");
      assertThat(CosmosRestClient.partitionKeyHeader(null)).isEqualTo("[null]");
      assertThat(CosmosRestClient.partitionKeyHeader(PartitionKeyValue.UNDEFINED)).isEqualTo("[{}]");
    }

    @Test
    void nonAsciiPartitionKeysAreSentEscapedLikeJsonStringifyAndEscapeNonAscii() throws Exception {
      server.enqueue(container(EVENTS, "/entityTypeName_entityID_kind"))
          .enqueue(Response.json(201, "{}"))
          .enqueue(Response.json(201, "{}"))
          .enqueue(Fixtures.response("cosmos/read-model-document.json", 200));
      CosmosRestClient client = client();

      events(client).items().create(obj("entityTypeName_entityID_kind", "Product-日本-event", "id", "1"));
      events(client).items().create(obj("entityTypeName_entityID_kind", "Product-ñ\u007f-event", "id", "2"));
      client.database(DB).container("new-booster-app-app-CartReadModel").item("日本", "日本").read();

      assertThat(server.request(1).header("x-ms-documentdb-partitionkey"))
          .isEqualTo("[\"Product-\\u65e5\\u672c-event\"]");
      assertThat(server.request(1).json())
          .isEqualTo(obj("entityTypeName_entityID_kind", "Product-日本-event", "id", "1"));
      assertThat(server.request(2).header("x-ms-documentdb-partitionkey"))
          .isEqualTo("[\"Product-\\u00f1\\u007f-event\"]");
      RecordedRequest read = server.request(3);
      assertSigned(read, "GET", "docs", "dbs/" + DB + "/colls/new-booster-app-app-CartReadModel/docs/日本");
      assertThat(read.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"\\u65e5\\u672c\"]");
      assertThat(CosmosRestClient.partitionKeyHeader("😀\"")).isEqualTo("[\"\\ud83d\\ude00\\\"\"]");
    }

    @Test
    void conflictsAreCosmosExceptionsWithTheServiceMessage() {
      server.enqueue(container("dispatched", "/eventId")).enqueue(Fixtures.response("cosmos/error-409.json", 409));

      assertThatThrownBy(() -> client().database(DB).container("dispatched").items().create(obj("eventId", "1")))
          .isInstanceOfSatisfying(CosmosException.class, error -> {
            assertThat(error.code()).isEqualTo(409);
            assertThat(error.getMessage()).startsWith("Entity with the specified id already exists in the system.");
          });
    }

    @Test
    void readReturnsTheDocumentOrA404ResponseWithoutResource() throws Exception {
      server.enqueue(Fixtures.response("cosmos/read-model-document.json", 200))
          .enqueue(Fixtures.response("cosmos/error-404.json", 404));
      CosmosContainer readModels = client().database(DB).container("new-booster-app-app-CartReadModel");

      ItemResponse found = readModels.item("42", "42").read();
      ItemResponse missing = readModels.item("43", "43").read();

      String link = "dbs/" + DB + "/colls/new-booster-app-app-CartReadModel/docs/42";
      assertSigned(server.request(0), "GET", "docs", link);
      assertThat(server.request(0).path()).isEqualTo("/" + link);
      assertThat(server.request(0).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"42\"]");
      assertThat(found.statusCode()).isEqualTo(200);
      assertThat(found.resource()).containsEntry("id", "42");
      assertThat(missing.statusCode()).isEqualTo(404);
      assertThat(missing.resource()).isNull();
    }

    @Test
    void replaceAndDeleteAddressTheDocumentWithItsPartitionKeyAndEncodeTheId() throws Exception {
      server.enqueue(Response.json(200, "{\"id\":\"a b\"}")).enqueue(Response.empty(204));
      CosmosContainer container = events(client());

      container.item("a b", "Cart-42-event").replace(obj("id", "a b", "value", obj()));
      ItemResponse deleted = container.item("a b", "Cart-42-snapshot").delete();

      RecordedRequest replace = server.request(0);
      assertSigned(replace, "PUT", "docs", COLL_LINK + "/docs/a b");
      assertThat(replace.rawPath()).isEqualTo("/" + COLL_LINK + "/docs/a%20b");
      assertThat(replace.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
      assertThat(replace.json()).isEqualTo(obj("id", "a b", "value", obj()));
      assertSigned(server.request(1), "DELETE", "docs", COLL_LINK + "/docs/a b");
      assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-snapshot\"]");
      assertThat(deleted.statusCode()).isEqualTo(204);
    }

    @Test
    void deletingAMissingDocumentThrowsA404() {
      server.enqueue(Fixtures.response("cosmos/error-404.json", 404));

      assertThatThrownBy(() -> events(client()).item("x", "pk").delete())
          .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(404));
    }

    @Test
    void upsertSendsTheUpsertHeaderAndTheIfMatchCondition() throws Exception {
      String readModels = "new-booster-app-app-CartReadModel";
      server.enqueue(container(readModels, "/id")).enqueue(Fixtures.response("cosmos/read-model-document.json", 200))
          .enqueue(Fixtures.response("cosmos/error-412.json", 412));
      CosmosContainer container = client().database(DB).container(readModels);
      Map<String, Object> readModel = obj("id", "42", "boosterMetadata", obj("version", 3));

      container.items().upsert(readModel,
          RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch("\"etag-1\"")));

      RecordedRequest upsert = server.request(1);
      assertSigned(upsert, "POST", "docs", "dbs/" + DB + "/colls/" + readModels);
      assertThat(upsert.header("x-ms-documentdb-is-upsert")).isEqualTo("True");
      assertThat(upsert.header("if-match")).isEqualTo("\"etag-1\"");
      assertThat(upsert.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"42\"]");
      assertThat(upsert.json()).isEqualTo(readModel);
      assertThatThrownBy(() -> container.items().upsert(readModel,
          RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch("*"))))
          .isInstanceOfSatisfying(CosmosException.class, error -> {
            assertThat(error.code()).isEqualTo(412);
            assertThat(error.getMessage()).startsWith("Operation cannot be performed because one of the specified");
          });
      assertThat(server.request(2).header("if-match")).isEqualTo("*");
    }
  }

  @Nested
  class Batches {

    @Test
    void sendsAnAtomicBatchWithGeneratedIdsAndRequestOptions() throws Exception {
      server.enqueue(Fixtures.response("cosmos/batch-200.json", 200).withHeader("x-ms-request-charge", "6.29"));

      BatchResponse response = events(client()).items().batch(
          List.of(OperationInput.create(obj("kind", "event", "entityTypeName_entityID_kind", "Cart-42-event"))),
          "Cart-42-event",
          new RequestOptions(null, "Session", true, "Include"));

      RecordedRequest batch = server.request(0);
      assertSigned(batch, "POST", "docs", COLL_LINK);
      assertThat(batch.path()).isEqualTo("/" + COLL_LINK + "/docs");
      assertThat(batch.header("x-ms-cosmos-is-batch-request")).isEqualTo("True");
      assertThat(batch.header("x-ms-cosmos-batch-atomic")).isEqualTo("True");
      assertThat(batch.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
      assertThat(batch.header("x-ms-consistency-level")).isEqualTo("Session");
      assertThat(batch.header("x-ms-documentdb-disable-ru-per-minute-usage")).isEqualTo("true");
      assertThat(batch.header("x-ms-indexing-directive")).isEqualTo("Include");
      assertThat(batch.json()).isEqualTo(List.of(obj("operationType", "Create", "resourceBody",
          obj("kind", "event", "entityTypeName_entityID_kind", "Cart-42-event", "id", "generated-1"))));
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.result()).containsExactly(new BatchResponse.OperationResponse(201, 6.29,
          "\"0800f1c2-0000-0d00-0000-65a000000010\"", obj("id", "generated-1")));
    }

    @Test
    void aRolledBackBatchIsReturnedWithItsStatusAndSubstatus() {
      server.enqueue(Fixtures.response("cosmos/batch-207.json", 207).withHeader("x-ms-substatus", "0"));

      BatchResponse response = events(client()).items().batch(
          List.of(OperationInput.create(obj("id", "a")), OperationInput.create(obj("id", "b"))), "pk",
          RequestOptions.none());

      assertThat(response.code()).isEqualTo(207);
      assertThat(response.substatus()).isZero();
      assertThat(response.result()).extracting(BatchResponse.OperationResponse::statusCode).containsExactly(409, 424);
      assertThat(server.request(0).header("x-ms-consistency-level")).isNull();
    }
  }

  @Nested
  class Throttling {

    @Test
    void retriesThrottledRequestsAfterTheServerRetryAfter() {
      server.enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "12"))
          .enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "30"))
          .enqueue(Response.json(200, "{\"id\":\"1\"}"));

      ItemResponse response = events(client()).item("1", "pk").read();

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(sleeps).containsExactly(12L, 30L);
      assertThat(server.requests()).hasSize(3);
    }

    @Test
    void usesTheFixedIntervalWhenConfigured() {
      server.enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "12"))
          .enqueue(Response.json(200, "{}"));

      events(client(new CosmosRetryOptions(9, 50, 30))).item("1", "pk").read();

      assertThat(sleeps).containsExactly(50L);
    }

    @Test
    void givesUpAfterMaxRetryAttemptCount() {
      server.enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "5"))
          .enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "5"));

      assertThatThrownBy(() -> events(client(new CosmosRetryOptions(1, 0, 30))).item("1", "pk").read())
          .isInstanceOfSatisfying(CosmosException.class, error -> {
            assertThat(error.code()).isEqualTo(429);
            assertThat(error.retryAfterInMs()).isEqualTo(5L);
          });
      assertThat(sleeps).containsExactly(5L);
    }

    @Test
    void givesUpWhenTheCumulativeWaitExceedsTheMaximum() {
      server.enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "1500"))
          .enqueue(Fixtures.response("cosmos/error-429.json", 429).withHeader("x-ms-retry-after-ms", "1500"));

      assertThatThrownBy(() -> events(client(new CosmosRetryOptions(9, 0, 1))).item("1", "pk").read())
          .isInstanceOf(CosmosException.class);
      assertThat(sleeps).containsExactly(1500L);
    }

    @Test
    void readsTheRetryOptionsFromTheAppSettingsLikeIndexTs() {
      assertThat(CosmosRetryOptions.fromSettings(Map.<String, String>of()::get)).isEqualTo(CosmosRetryOptions.defaults());
      assertThat(CosmosRetryOptions.fromSettings(Map.of("COSMOSDB_MAX_RETRIES", "3", "COSMOSDB_RETRY_INTERVAL", "",
          "COSMOSDB_MAX_WAIT_TIME", "abc")::get))
          .satisfies(options -> {
            assertThat(options.maxRetryAttemptCount()).isEqualTo(3);
            assertThat(options.fixedRetryIntervalInMilliseconds()).isZero();
            assertThat(options.maxWaitTimeInSeconds()).isNaN();
          });
    }
  }

  /** The @azure/cosmos retry policies other than throttling: DefaultRetryPolicy, TimeoutFailoverRetryPolicy, 410. */
  @Nested
  class TransientFailures {

    private final SqlQuerySpec query = new SqlQuerySpec("SELECT * FROM c", List.of());

    @Test
    void connectionFailuresOfQueriesAreRetriedOneSecondApart() {
      server.enqueue(Response.dropConnection()).enqueue(Response.dropConnection())
          .enqueue(Response.json(200, "{\"Documents\":[{\"id\":\"1\"}]}"));

      FeedResponse response = events(client()).items().query(query, FeedOptions.forPartition("pk")).fetchAll();

      assertThat(response.resources()).containsExactly(obj("id", "1"));
      assertThat(server.requests()).hasSize(3);
      assertThat(sleeps).containsExactly(1000L, 1000L);
    }

    @Test
    void connectionFailuresOfPointReadsAreRetried() {
      // The JDK client itself retries an idempotent GET whose connection closed without an answer, before the
      // transport policy sees the failure; either way the read succeeds
      server.enqueue(Response.dropConnection()).enqueue(Response.dropConnection())
          .enqueue(Response.json(200, "{\"id\":\"1\"}"));

      ItemResponse response = events(client()).item("1", "pk").read();

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(server.requests()).hasSize(3);
    }

    @Test
    void connectionFailuresOfQueriesAreRetriedAndGiveUpAfterTenRetries() {
      for (int i = 0; i < 11; i++) {
        server.enqueue(Response.dropConnection());
      }

      assertThatThrownBy(() -> events(client()).items().query(query, FeedOptions.forPartition("pk")).fetchAll())
          .isInstanceOf(java.io.UncheckedIOException.class);
      assertThat(server.requests()).hasSize(11);
      assertThat(sleeps).hasSize(10).containsOnly(1000L);
    }

    @Test
    void connectionFailuresOfWritesAreNotRetriedAsTheSdkDoesNotRetryThem() {
      server.enqueue(container(EVENTS, "/entityTypeName_entityID_kind")).enqueue(Response.dropConnection());

      assertThatThrownBy(() -> events(client()).items().create(obj("entityTypeName_entityID_kind", "pk", "id", "1")))
          .isInstanceOf(java.io.UncheckedIOException.class);
      assertThat(server.requests()).hasSize(2);
      assertThat(sleeps).isEmpty();
    }

    @Test
    void serviceUnavailableAndRequestTimeoutOfReadsAreRetriedOnce() {
      server.enqueue(Response.json(503, "{\"message\":\"busy\"}")).enqueue(Response.json(200, "{\"id\":\"1\"}"))
          .enqueue(Response.json(408, "{\"message\":\"timeout\"}")).enqueue(Response.json(200, "{\"Documents\":[]}"))
          .enqueue(Response.json(503, "{\"message\":\"busy\"}")).enqueue(Response.json(503, "{\"message\":\"busy\"}"));

      assertThat(events(client()).item("1", "pk").read().statusCode()).isEqualTo(200);
      assertThat(events(client()).items().query(query, FeedOptions.forPartition("pk")).fetchAll().resources()).isEmpty();
      assertThatThrownBy(() -> events(client()).item("1", "pk").read())
          .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(503));
      assertThat(server.requests()).hasSize(6);
      assertThat(sleeps).isEmpty();
    }

    @Test
    void serviceUnavailableOfWritesIsNotRetried() {
      server.enqueue(Response.json(503, "{\"message\":\"busy\"}"));

      assertThatThrownBy(() -> events(client()).item("1", "pk").replace(obj("id", "1")))
          .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(503));
      assertThat(server.requests()).hasSize(1);
    }

    @Test
    void aRangeGoneAfterASplitIsRepairedByReadingTheRangesAgain() {
      String plan = Fixtures.read("cosmos/query-plan-order-by.json")
          .replace("\"offset\": 0", "\"offset\": null").replace("\"limit\": 3", "\"limit\": null")
          .replace("\\nOFFSET 0 LIMIT 3", "");
      Map<String, Object> twoRanges = BoosterJson.parseObject(Fixtures.read("cosmos/pkranges-two.json"));
      List<?> ranges = (List<?>) twoRanges.get("PartitionKeyRanges");
      @SuppressWarnings("unchecked")
      Map<String, Object> left = new java.util.LinkedHashMap<>((Map<String, Object>) ranges.get(0));
      @SuppressWarnings("unchecked")
      Map<String, Object> right = new java.util.LinkedHashMap<>((Map<String, Object>) ranges.get(1));
      Map<String, Object> rightLow = new java.util.LinkedHashMap<>(right);
      rightLow.putAll(obj("id", "3", "maxExclusive", "BFFFFFFFFFFFFFFF", "parents", List.of("0", "2")));
      Map<String, Object> rightHigh = new java.util.LinkedHashMap<>(right);
      rightHigh.putAll(obj("id", "4", "minInclusive", "BFFFFFFFFFFFFFFF", "parents", List.of("0", "2")));
      server.enqueue(Response.json(200, plan)).enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(orderByRow("a", 1)))
          .enqueue(Response.json(410, "{\"message\":\"The requested partition key range is gone\"}")
              .withHeader("x-ms-substatus", "1002"))
          // the feed lists the split range itself next to its children: the children win
          .enqueue(Response.json(200, BoosterJson.toJson(
              obj("PartitionKeyRanges", List.of(left, right, rightLow, rightHigh)))))
          .enqueue(documents(orderByRow("a", 1)))
          .enqueue(documents(orderByRow("b", 2)))
          .enqueue(documents(orderByRow("c", 3)));

      FeedResponse response = events(client()).items().query(new SqlQuerySpec(
          "SELECT * FROM c ORDER BY c.createdAt DESC", List.of())).fetchAll();

      assertThat(response.resources()).containsExactly(obj("id", "c"), obj("id", "b"), obj("id", "a"));
      assertThat(server.requests()).hasSize(8);
      assertThat(server.request(6).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("3");
      assertThat(server.request(7).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("4");
    }

    @Test
    void otherGoneErrorsAreThrown() {
      server.enqueue(Response.json(410, "{\"message\":\"gone\"}").withHeader("x-ms-substatus", "1000"));

      assertThatThrownBy(() -> events(client()).item("1", "pk").read())
          .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(410));
      assertThat(server.requests()).hasSize(1);
    }

    private Response documents(Object... documents) {
      return Response.json(200, BoosterJson.toJson(obj("Documents", List.of(documents))));
    }

    private Object orderByRow(String id, int orderValue) {
      return obj("orderByItems", List.of(obj("item", orderValue)), "payload", obj("id", id));
    }
  }

  @Nested
  class Queries {

    private final SqlQuerySpec spec = new SqlQuerySpec(
        "SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = @partitionKey",
        List.of(SqlParameter.of("@partitionKey", "Cart-42-event"), SqlParameter.of("@fromTime", null)));

    private Response documents(String continuation, Object... documents) {
      Response response = Response.json(200, BoosterJson.toJson(
          obj("_rid", "PaYSAPH7qAo=", "Documents", List.of(documents), "_count", documents.length)));
      return continuation == null ? response : response.withHeader("x-ms-continuation", continuation);
    }

    @Test
    void singlePartitionQueriesFollowContinuationTokens() throws Exception {
      server.enqueue(documents("token-1", obj("id", "1"))).enqueue(documents(null, obj("id", "2")));

      FeedResponse response = events(client()).items().query(spec, FeedOptions.forPartition("Cart-42-event")).fetchAll();

      assertThat(response.resources()).containsExactly(obj("id", "1"), obj("id", "2"));
      assertThat(response.continuationToken()).isNull();
      RecordedRequest first = server.request(0);
      assertSigned(first, "POST", "docs", COLL_LINK);
      assertThat(first.header("x-ms-documentdb-isquery")).isEqualTo("True");
      assertThat(first.header("content-type")).isEqualTo("application/query+json");
      assertThat(first.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
      assertThat(first.header("x-ms-documentdb-query-enablecrosspartition")).isNull();
      assertThat(first.header("x-ms-continuation")).isNull();
      assertThat(first.body()).isEqualTo("{\"query\":\"SELECT * FROM c WHERE c[\\\"entityTypeName_entityID_kind\\\"] = "
          + "@partitionKey\",\"parameters\":[{\"name\":\"@partitionKey\",\"value\":\"Cart-42-event\"},"
          + "{\"name\":\"@fromTime\",\"value\":null}]}");
      assertThat(server.request(1).header("x-ms-continuation")).isEqualTo("token-1");
    }

    @Test
    void crossPartitionQueriesAskForAQueryPlanAndLetTheGatewayServeSimpleOnes() throws Exception {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(documents("token-1", obj("id", "1"), obj("id", "2")));

      FeedResponse page = events(client()).items().query(spec, new FeedOptions(null, 2, null)).fetchNext();

      RecordedRequest plan = server.request(0);
      assertSigned(plan, "POST", "docs", COLL_LINK);
      assertThat(plan.header("x-ms-cosmos-is-query-plan-request")).isEqualTo("True");
      assertThat(plan.header("x-ms-cosmos-supported-query-features"))
          .isEqualTo("Aggregate, CompositeAggregate, Distinct, MultipleOrderBy, OffsetAndLimit, OrderBy, Top");
      assertThat(plan.header("x-ms-cosmos-query-version")).isEqualTo("1.4");
      assertThat(plan.header("x-ms-documentdb-query-enablecrosspartition")).isEqualTo("True");
      assertThat(plan.header("x-ms-documentdb-partitionkey")).isNull();
      assertThat(plan.json()).isEqualTo(BoosterJson.toRaw(spec));
      RecordedRequest query = server.request(1);
      assertThat(query.header("x-ms-documentdb-query-enablecrosspartition")).isEqualTo("True");
      assertThat(query.header("x-ms-max-item-count")).isEqualTo("2");
      assertThat(query.header("x-ms-cosmos-is-query-plan-request")).isNull();
      assertThat(page.resources()).hasSize(2);
      assertThat(page.continuationToken()).isEqualTo("token-1");
      assertThat(page.hasMoreResults()).isTrue();
    }

    @Test
    void fetchNextFillsThePageAcrossShortGatewayPagesAndResumesFromAToken() {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(documents("t1", obj("id", "1")))
          .enqueue(documents("t2", obj("id", "2")))
          .enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(documents(null, obj("id", "3")));

      FeedResponse first = events(client()).items().query(spec, new FeedOptions(null, 2, null)).fetchNext();
      FeedResponse second = events(client()).items().query(spec, new FeedOptions("t2", 2, null)).fetchNext();

      assertThat(first.resources()).containsExactly(obj("id", "1"), obj("id", "2"));
      assertThat(first.continuationToken()).isEqualTo("t2");
      assertThat(server.request(2).header("x-ms-max-item-count")).isEqualTo("1");
      assertThat(server.request(2).header("x-ms-continuation")).isEqualTo("t1");
      assertThat(server.request(4).header("x-ms-continuation")).isEqualTo("t2");
      assertThat(second.resources()).containsExactly(obj("id", "3"));
      assertThat(second.continuationToken()).isNull();
    }

    @Test
    void orderByQueriesRunOnEveryRangeAndAreMergedByTheClient() throws Exception {
      SqlQuerySpec orderBy = new SqlQuerySpec("SELECT * FROM c ORDER BY c.createdAt DESC OFFSET 0 LIMIT 3", List.of());
      server.enqueue(Fixtures.response("cosmos/query-plan-order-by.json", 200))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null,
              obj("_rid", "a", "orderByItems", List.of(obj("item", "2026-01-04")), "payload", obj("id", "a4")),
              obj("_rid", "b", "orderByItems", List.of(obj("item", "2026-01-01")), "payload", obj("id", "a1"))))
          .enqueue(documents("r2", obj("_rid", "c", "orderByItems", List.of(obj("item", "2026-01-03")),
              "payload", obj("id", "b3"))))
          .enqueue(documents(null, obj("_rid", "d", "orderByItems", List.of(obj("item", "2026-01-02")),
              "payload", obj("id", "b2"))));

      FeedResponse response = events(client()).items().query(orderBy).fetchAll();

      assertThat(response.resources()).containsExactly(obj("id", "a4"), obj("id", "b3"), obj("id", "b2"));
      RecordedRequest ranges = server.request(1);
      assertSigned(ranges, "GET", "pkranges", COLL_LINK);
      assertThat(ranges.path()).isEqualTo("/" + COLL_LINK + "/pkranges");
      RecordedRequest range1 = server.request(2);
      assertThat(range1.header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("1");
      assertThat(range1.json()).isEqualTo(obj("query",
          "SELECT c._rid, [{\"item\": c.createdAt}] AS orderByItems, c AS payload\nFROM c\nWHERE (true)\n"
              + "ORDER BY c.createdAt DESC\nOFFSET 0 LIMIT 3", "parameters", List.of()));
      assertThat(server.request(3).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("2");
      assertThat(server.request(4).header("x-ms-continuation")).isEqualTo("r2");
    }

    @Test
    void distinctQueriesAreDeduplicatedThenOffsetAndLimitedByTheClient() {
      server.enqueue(Fixtures.response("cosmos/query-plan-distinct.json", 200))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, obj("entityID", "1"), obj("entityID", "2")))
          .enqueue(documents(null, obj("entityID", "2"), obj("entityID", "3"), obj("entityID", "4")));

      FeedResponse response = events(client()).items()
          .query(new SqlQuerySpec("SELECT DISTINCT c.entityID FROM c OFFSET 1 LIMIT 2 ", List.of())).fetchAll();

      assertThat(response.resources()).containsExactly(obj("entityID", "2"), obj("entityID", "3"));
    }

    @Test
    void valueCountAggregatesAreSummedAcrossRanges() {
      server.enqueue(Fixtures.response("cosmos/query-plan-count.json", 200))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, List.of(obj("item", 3))))
          .enqueue(documents(null, List.of(obj("item", 4))));

      FeedResponse response =
          events(client()).items().query(new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c", List.of())).fetchAll();

      assertThat(response.resources()).containsExactly(7);
      assertThat(server.request(2).json())
          .isEqualTo(obj("query", "SELECT VALUE [{\"item\": COUNT(1)}]\nFROM c", "parameters", List.of()));
    }

    @Test
    void nonValueAggregatesAndGroupByAreRejectedInsteadOfReturningPartialResults() {
      String nonValuePlan = Fixtures.read("cosmos/query-plan-count.json")
          .replace("\"hasSelectValue\": true", "\"hasSelectValue\": false");
      server.enqueue(Response.json(200, nonValuePlan));
      assertThatThrownBy(() -> events(client()).items()
          .query(new SqlQuerySpec("SELECT COUNT(1) as total FROM c", List.of())).fetchAll())
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("Cross-partition aggregate queries must use SELECT VALUE with the Booster Cosmos DB REST client: "
              + "SELECT COUNT(1) as total FROM c");

      String groupByPlan = Fixtures.read("cosmos/query-plan-count.json")
          .replace("\"groupByExpressions\": []", "\"groupByExpressions\": [\"c.kind\"]");
      server.enqueue(Response.json(200, groupByPlan));
      assertThatThrownBy(() -> events(client()).items()
          .query(new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c GROUP BY c.kind", List.of())).fetchAll())
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageStartingWith("Cross-partition GROUP BY queries are not supported");
    }

    @Test
    void topIsAppliedAfterMergingTheRanges() {
      server.enqueue(Fixtures.response("cosmos/query-plan-top.json", 200))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, 1))
          .enqueue(documents(null, 1));

      FeedResponse response =
          events(client()).items().query(new SqlQuerySpec("SELECT TOP 1 1 FROM c", List.of())).fetchAll();

      assertThat(response.resources()).containsExactly(1);
      assertThat(server.request(2).json()).isEqualTo(obj("query", "SELECT TOP 1 1 FROM c", "parameters", List.of()));
    }

    @Test
    void onlyTheRangesOverlappingTheQueryRangesAreQueried() {
      server.enqueue(Fixtures.response("cosmos/query-plan-point.json", 200))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, obj("_rid", "a", "orderByItems", List.of(obj("item", "x")), "payload", obj("id", "1"))));

      FeedResponse response = events(client()).items().query(spec).fetchAll();

      assertThat(server.requests()).hasSize(3);
      assertThat(server.request(2).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("1");
      assertThat(response.resources()).containsExactly(obj("id", "1"));
    }

    @Test
    void aGatewayRefusalDuringFetchNextFallsBackToAPagedPipeline() {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(Fixtures.response("cosmos/error-400-not-servable.json", 400).withHeader("x-ms-substatus", "1004"))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, obj("id", "1"), obj("id", "2")))
          .enqueue(documents(null, obj("id", "3")));

      FeedResponse first = events(client()).items().query(spec, new FeedOptions(null, 2, null)).fetchNext();

      assertThat(first.resources()).containsExactly(obj("id", "1"), obj("id", "2"));
      assertThat(first.continuationToken()).isEqualTo("booster-pipeline-skip:2");
      assertThat(server.request(3).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("1");
      assertThat(server.request(4).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("2");
    }

    @Test
    void theClientPipelinePagesWithItsOwnTokens() {
      SqlQuerySpec orderBy = new SqlQuerySpec("SELECT * FROM c ORDER BY c.createdAt DESC", List.of());
      Response range1 = documents(null,
          obj("orderByItems", List.of(obj("item", 3)), "payload", obj("id", "3")),
          obj("orderByItems", List.of(obj("item", 1)), "payload", obj("id", "1")));
      Response range2 = documents(null, obj("orderByItems", List.of(obj("item", 2)), "payload", obj("id", "2")));
      String plan = Fixtures.read("cosmos/query-plan-order-by.json")
          .replace("\"offset\": 0", "\"offset\": null").replace("\"limit\": 3", "\"limit\": null");
      server.enqueue(Response.json(200, plan)).enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(range1).enqueue(range2)
          .enqueue(Response.json(200, plan)).enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(range1).enqueue(range2);

      FeedResponse first = events(client()).items().query(orderBy, new FeedOptions(null, 2, null)).fetchNext();
      FeedResponse second = events(client()).items()
          .query(orderBy, new FeedOptions(first.continuationToken(), 2, null)).fetchNext();

      assertThat(first.resources()).containsExactly(obj("id", "3"), obj("id", "2"));
      assertThat(first.continuationToken()).isEqualTo("booster-pipeline-skip:2");
      assertThat(second.resources()).containsExactly(obj("id", "1"));
      assertThat(second.continuationToken()).isNull();
    }

    private Response row(String id, int orderValue) {
      return documents(null, obj("orderByItems", List.of(obj("item", orderValue)), "payload", obj("id", id)));
    }

    private Object orderByRow(String id, int orderValue) {
      return obj("orderByItems", List.of(obj("item", orderValue)), "payload", obj("id", id));
    }

    @Test
    void aPagedOrderByQueryReadsAtMostSkipPlusPagePlusOneItemsOfEachRange() {
      SqlQuerySpec orderBy = new SqlQuerySpec("SELECT * FROM c ORDER BY c.createdAt DESC", List.of());
      String plan = Fixtures.read("cosmos/query-plan-order-by.json")
          .replace("\"offset\": 0", "\"offset\": null").replace("\"limit\": 3", "\"limit\": null")
          .replace("\\nOFFSET 0 LIMIT 3", "");
      server.enqueue(Response.json(200, plan)).enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          // range 1 holds 9, 7, 5, 3, 1 (more pages behind "r1-3"); range 2 holds 8 and nothing else
          .enqueue(documents("r1-2", orderByRow("9", 9), orderByRow("7", 7)))
          .enqueue(documents("r1-3", orderByRow("5", 5)))
          .enqueue(documents(null, orderByRow("8", 8)))
          .enqueue(Response.json(200, plan)).enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents("r1-3", orderByRow("9", 9), orderByRow("7", 7), orderByRow("5", 5)))
          .enqueue(documents("r1-4", orderByRow("3", 3)))
          .enqueue(documents(null, orderByRow("1", 1)))
          .enqueue(row("8", 8));

      FeedResponse first = events(client()).items().query(orderBy, new FeedOptions(null, 2, null)).fetchNext();
      FeedResponse second = events(client()).items()
          .query(orderBy, new FeedOptions(first.continuationToken(), 2, null)).fetchNext();

      assertThat(first.resources()).containsExactly(obj("id", "9"), obj("id", "8"));
      assertThat(first.continuationToken()).isEqualTo("booster-pipeline-skip:2");
      assertThat(server.requests()).hasSize(11);
      // first page: 0 + 2 + 1 = 3 items per range at most, asked with x-ms-max-item-count, "r1-3" never followed
      assertThat(server.request(2).header("x-ms-max-item-count")).isEqualTo("3");
      assertThat(server.request(3).header("x-ms-continuation")).isEqualTo("r1-2");
      assertThat(server.request(3).header("x-ms-max-item-count")).isEqualTo("1");
      assertThat(server.request(4).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("2");
      assertThat(server.request(4).header("x-ms-max-item-count")).isEqualTo("3");
      // second page: 2 + 2 + 1 = 5 per range
      assertThat(server.request(7).header("x-ms-max-item-count")).isEqualTo("5");
      assertThat(server.request(8).header("x-ms-max-item-count")).isEqualTo("2");
      assertThat(server.request(9).header("x-ms-max-item-count")).isEqualTo("1");
      assertThat(server.request(10).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("2");
      assertThat(second.resources()).containsExactly(obj("id", "7"), obj("id", "5"));
      assertThat(second.continuationToken()).isEqualTo("booster-pipeline-skip:4");
    }

    @Test
    void aPagedPlainPipelineQueryStopsReadingRangesOnceThePageIsKnown() {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(Fixtures.response("cosmos/error-400-not-servable.json", 400).withHeader("x-ms-substatus", "1004"))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents("more", obj("id", "1"), obj("id", "2"), obj("id", "3")));

      FeedResponse first = events(client()).items().query(spec, new FeedOptions(null, 2, null)).fetchNext();

      assertThat(first.resources()).containsExactly(obj("id", "1"), obj("id", "2"));
      assertThat(first.continuationToken()).isEqualTo("booster-pipeline-skip:2");
      assertThat(server.requests()).hasSize(4);
      assertThat(server.request(3).header("x-ms-max-item-count")).isEqualTo("3");
    }

    @Test
    void theGatewayRefusingAQueryFallsBackToThePipeline() {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(Fixtures.response("cosmos/error-400-not-servable.json", 400).withHeader("x-ms-substatus", "1004"))
          .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
          .enqueue(documents(null, obj("id", "1")))
          .enqueue(documents(null, obj("id", "2")));

      FeedResponse response = events(client()).items().query(spec).fetchAll();

      assertThat(response.resources()).containsExactly(obj("id", "1"), obj("id", "2"));
      assertThat(server.request(3).header("x-ms-documentdb-partitionkeyrangeid")).isEqualTo("1");
    }

    @Test
    void otherBadRequestsAreThrown() {
      server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
          .enqueue(Response.json(400, "{\"code\":\"BadRequest\",\"message\":\"Syntax error, incorrect syntax near 'FORM'.\"}"));

      assertThatThrownBy(() -> events(client()).items().query(spec).fetchAll())
          .isInstanceOfSatisfying(CosmosException.class, error -> {
            assertThat(error.code()).isEqualTo(400);
            assertThat(error.getMessage()).isEqualTo("Syntax error, incorrect syntax near 'FORM'.");
          });
    }

    @Test
    void partitionKeyRangesArePaged() {
      String firstRanges = Fixtures.read("cosmos/pkranges-two.json");
      Map<String, Object> parsed = BoosterJson.parseObject(firstRanges);
      List<?> ranges = (List<?>) parsed.get("PartitionKeyRanges");
      server.enqueue(Fixtures.response("cosmos/query-plan-count.json", 200))
          .enqueue(Response.json(200, BoosterJson.toJson(obj("PartitionKeyRanges", List.of(ranges.get(0)))))
              .withHeader("x-ms-continuation", "ranges-2"))
          .enqueue(Response.json(200, BoosterJson.toJson(obj("PartitionKeyRanges", List.of(ranges.get(1))))))
          .enqueue(documents(null, List.of(obj("item", 1))))
          .enqueue(documents(null, List.of(obj("item", 1))));

      FeedResponse response =
          events(client()).items().query(new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c", List.of())).fetchAll();

      assertThat(server.request(2).header("x-ms-continuation")).isEqualTo("ranges-2");
      assertThat(response.resources()).containsExactly(2);
    }
  }
}
