package com.boostercloud.integration.azurecontract;

import com.boostercloud.framework.types.BoosterJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * An in-JVM, <b>stateful</b> stand-in for the three Azure data-plane services a deployed Booster application talks to
 * (Cosmos DB, Event Hubs and Web PubSub), served over HTTP by {@code com.sun.net.httpserver} so that the production REST
 * clients of {@code framework-provider-azure} run unchanged against it.
 *
 * <p>Unlike the provider's own {@code FakeAzureServer} (a recorder answering queued responses, test sources of another
 * module), this fake keeps the documents it is sent and answers from them, which is what an end-to-end run of the
 * application's Azure Functions needs: the events stored by a mutation are the documents the change feed delivers, the
 * snapshot and read model writes of the stream consumer are what a later query reads, and so on.
 *
 * <ul>
 *   <li><b>Cosmos DB</b> (SQL REST API, {@code /dbs/<db>/colls/<coll>/...}): container definitions (only the containers
 *       {@link #createContainer created} by the test exist, as only the Terraform ones exist in Azure), document
 *       create/upsert (with {@code If-Match}), point reads, replace, delete, transactional batches, single-partition and
 *       cross-partition queries ({@link FakeCosmosSql}), query plans and partition key ranges (one range). It enforces
 *       what the service enforces: the master-key signature of every request, the partition key header matching the
 *       document, unique ids per logical partition, unique keys, the 400/1004 refusal of cross-partition queries the
 *       gateway cannot serve. Written documents get the system properties {@code _rid}, {@code _self}, {@code _etag},
 *       {@code _attachments} and {@code _ts}; {@link #changeFeed} returns them with {@code _lsn}, as the change feed
 *       trigger delivers them.
 *   <li><b>Event Hubs</b> ({@code POST /<hub>/messages}, "send batch"): checks the shared access signature and keeps
 *       the posted batch entries ({@code {Body, BrokerProperties}}) per hub.
 *   <li><b>Web PubSub</b> ({@code POST /api/hubs/<hub>/connections/<id>/:send}): checks the access-key JWT and keeps the
 *       messages sent to each connection.
 * </ul>
 *
 * Every request is recorded ({@link #requests()}); a request the fake cannot serve or refuses (unknown endpoint,
 * unsupported query, bad signature, ...) is answered with an error and also recorded in {@link #failures()}, which the
 * contract tests require to be empty.
 *
 * <p>The process-wide Azure clients of the provider are created once per JVM from the app settings, so there is one
 * fake per JVM ({@link #shared()}); tests {@link #reset()} its state.
 */
final class FakeAzureServices implements AutoCloseable {

  /** The Cosmos DB master key (the documented key of the Cosmos DB emulator). */
  static final String COSMOS_KEY =
      "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
  /** The Event Hubs shared access policy. */
  static final String EVENT_HUBS_KEY_NAME = "RootManageSharedAccessKey";
  /** The Event Hubs shared access key. */
  static final String EVENT_HUBS_KEY = "ZmFrZS1ldmVudC1odWJzLWtleQ==";
  /** The Web PubSub access key. */
  static final String WEB_PUB_SUB_KEY = "ZmFrZS13ZWItcHViLXN1Yi1rZXk=";

  /** A recorded request. */
  record Request(String service, String method, String path, String query, Map<String, String> headers, String body) {

    /**
     * @param name a header name (any case)
     * @return its value, or {@code null}
     */
    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the body parsed as JSON
     */
    Object json() {
      return BoosterJson.parse(body);
    }

    /**
     * @return {@code <METHOD> <path>} plus the Cosmos DB operation kind ({@code batch}, {@code query},
     *     {@code query-plan}, {@code upsert}) when there is one
     */
    String summary() {
      String kind = "";
      if ("True".equals(header("x-ms-cosmos-is-batch-request"))) {
        kind = " (batch)";
      } else if ("True".equals(header("x-ms-cosmos-is-query-plan-request"))) {
        kind = " (query plan)";
      } else if ("True".equals(header("x-ms-documentdb-isquery"))) {
        kind = " (query)";
      } else if ("True".equals(header("x-ms-documentdb-is-upsert"))) {
        kind = " (upsert)";
      }
      return method + " " + path + kind;
    }
  }

  /**
   * A message sent to a Web PubSub connection.
   *
   * @param hub the hub
   * @param connectionId the connection
   * @param contentType the content type
   * @param body the message
   */
  record WebPubSubMessage(String hub, String connectionId, String contentType, String body) {}

  private record Container(String database, String id, String partitionKeyPath, String uniqueKeyPath, String rid) {}

  private record Response(int status, Map<String, String> headers, String body) {
    static Response json(int status, Object body, String... headers) {
      Map<String, String> map = new LinkedHashMap<>();
      map.put("Content-Type", "application/json");
      for (int i = 0; i + 1 < headers.length; i += 2) {
        map.put(headers[i], headers[i + 1]);
      }
      return new Response(status, map, body == null ? "" : BoosterJson.toJson(body));
    }

    static Response error(int status, String code, String message, String... headers) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("code", code);
      body.put("message", message);
      return json(status, body, headers);
    }
  }

  private static FakeAzureServices shared;

  private final HttpServer server;
  private final Object lock = new Object();
  private final List<Request> requests = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private final Map<String, Container> containers = new LinkedHashMap<>();
  private final Map<String, List<Map<String, Object>>> documents = new LinkedHashMap<>();
  private final Map<String, List<Map<String, Object>>> changeFeeds = new LinkedHashMap<>();
  private final Map<String, List<Map<String, Object>>> eventHubMessages = new LinkedHashMap<>();
  private final List<WebPubSubMessage> webPubSubMessages = new ArrayList<>();
  private long sequence;

  private FakeAzureServices() {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
  }

  /**
   * @return the fake of this JVM, started on a free loopback port on first use and stopped when the JVM exits
   */
  static synchronized FakeAzureServices shared() {
    if (shared == null) {
      shared = new FakeAzureServices();
      FakeAzureServices started = shared;
      Runtime.getRuntime().addShutdownHook(new Thread(started::close, "fake-azure-services-stop"));
    }
    return shared;
  }

  /**
   * @return {@code http://127.0.0.1:<port>}
   */
  String baseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  /**
   * @return the {@code COSMOSDB_CONNECTION_STRING} app setting pointing at this fake
   */
  String cosmosConnectionString() {
    return "AccountEndpoint=" + baseUrl() + "/;AccountKey=" + COSMOS_KEY + ";";
  }

  /**
   * @param hub the event hub
   * @return the {@code EVENTHUB_CONNECTION_STRING} app setting pointing at this fake (the Terraform value has the
   *     same shape: the namespace connection string plus {@code ;EntityPath=<hub>})
   */
  String eventHubConnectionString(String hub) {
    return "Endpoint=" + baseUrl() + "/;SharedAccessKeyName=" + EVENT_HUBS_KEY_NAME + ";SharedAccessKey="
        + EVENT_HUBS_KEY + ";EntityPath=" + hub;
  }

  /**
   * @return the {@code WebPubSubConnectionString} app setting pointing at this fake
   */
  String webPubSubConnectionString() {
    return "Endpoint=" + baseUrl() + ";AccessKey=" + WEB_PUB_SUB_KEY + ";Version=1.0;";
  }

  /** Forgets every container, document, message and recorded request. */
  void reset() {
    synchronized (lock) {
      requests.clear();
      failures.clear();
      containers.clear();
      documents.clear();
      changeFeeds.clear();
      eventHubMessages.clear();
      webPubSubMessages.clear();
    }
  }

  /** Forgets the recorded requests and failures only. */
  void clearRequests() {
    synchronized (lock) {
      requests.clear();
      failures.clear();
    }
  }

  /**
   * Creates a container (what Terraform's {@code azurerm_cosmosdb_sql_container} does).
   *
   * @param database the database
   * @param id the container
   * @param partitionKeyPath its partition key path ({@code /id})
   * @param uniqueKeyPath its unique key path, or {@code null}
   */
  void createContainer(String database, String id, String partitionKeyPath, String uniqueKeyPath) {
    synchronized (lock) {
      String key = database + "/" + id;
      containers.put(key, new Container(database, id, partitionKeyPath, uniqueKeyPath, rid()));
      documents.put(key, new ArrayList<>());
      changeFeeds.put(key, new ArrayList<>());
    }
  }

  /**
   * @param database the database
   * @param container the container
   * @return copies of its documents (system properties included), in creation order
   */
  List<Map<String, Object>> documents(String database, String container) {
    synchronized (lock) {
      return copies(documents.getOrDefault(database + "/" + container, List.of()));
    }
  }

  /**
   * @param database the database
   * @param container the container
   * @return what a change feed trigger delivers: every created or replaced document version, in order, with its
   *     {@code _lsn}
   */
  List<Map<String, Object>> changeFeed(String database, String container) {
    synchronized (lock) {
      return copies(changeFeeds.getOrDefault(database + "/" + container, List.of()));
    }
  }

  /**
   * @param hub an event hub
   * @return the batch entries posted to it ({@code {Body, BrokerProperties}}), in order
   */
  List<Map<String, Object>> eventHubMessages(String hub) {
    synchronized (lock) {
      return copies(eventHubMessages.getOrDefault(hub, List.of()));
    }
  }

  /**
   * @return the messages sent to Web PubSub connections, in order
   */
  List<WebPubSubMessage> webPubSubMessages() {
    synchronized (lock) {
      return List.copyOf(webPubSubMessages);
    }
  }

  /**
   * @return every request received, in order
   */
  List<Request> requests() {
    synchronized (lock) {
      return List.copyOf(requests);
    }
  }

  /**
   * @param service {@code cosmos}, {@code eventhubs} or {@code webpubsub}
   * @return the requests to that service, in order
   */
  List<Request> requests(String service) {
    return requests().stream().filter(request -> request.service().equals(service)).toList();
  }

  /**
   * @return the requests the fake refused or could not serve, with the reason
   */
  List<String> failures() {
    synchronized (lock) {
      return List.copyOf(failures);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Dispatch

  private void handle(HttpExchange exchange) throws IOException {
    try (InputStream input = exchange.getRequestBody()) {
      String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      Map<String, String> headers = new LinkedHashMap<>();
      exchange.getRequestHeaders().forEach((name, values) ->
          headers.put(name.toLowerCase(Locale.ROOT), values.isEmpty() ? "" : values.getFirst()));
      URI uri = exchange.getRequestURI();
      String path = uri.getPath();
      String service = path.startsWith("/dbs/") ? "cosmos"
          : path.startsWith("/api/hubs/") ? "webpubsub"
          : path.endsWith("/messages") ? "eventhubs" : "unknown";
      Request request = new Request(service, exchange.getRequestMethod(), path, uri.getRawQuery(), headers, body);
      Response response;
      synchronized (lock) {
        requests.add(request);
        try {
          response = switch (service) {
            case "cosmos" -> cosmos(request);
            case "eventhubs" -> eventHubs(request);
            case "webpubsub" -> webPubSub(request);
            default -> fail(request, 404, "NotFound", "No fake Azure service at " + path);
          };
        } catch (FakeCosmosSql.UnsupportedQueryException e) {
          response = fail(request, 400, "BadRequest", "The fake Cosmos DB does not support the query: " + e.getMessage());
        } catch (RuntimeException e) {
          response = fail(request, 500, "InternalServerError", "The fake Azure services failed: " + e);
        }
      }
      response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
      byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        try (OutputStream output = exchange.getResponseBody()) {
          output.write(bytes);
        }
      }
    } finally {
      exchange.close();
    }
  }

  private Response fail(Request request, int status, String code, String message, String... headers) {
    failures.add(request.summary() + ": " + status + " " + message);
    return Response.error(status, code, message, headers);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Cosmos DB

  private Response cosmos(Request request) {
    String[] segments = request.path().substring(1).split("/");
    if (segments.length < 4 || !segments[0].equals("dbs") || !segments[2].equals("colls")) {
      return fail(request, 404, "NotFound", "Unknown Cosmos DB resource " + request.path());
    }
    String database = segments[1];
    String containerId = segments[3];
    String containerLink = "dbs/" + database + "/colls/" + containerId;
    String resourceType;
    String resourceLink;
    String documentId = null;
    if (segments.length == 4) {
      resourceType = "colls";
      resourceLink = containerLink;
    } else if (segments.length == 5 && segments[4].equals("docs")) {
      resourceType = "docs";
      resourceLink = containerLink;
    } else if (segments.length == 6 && segments[4].equals("docs")) {
      resourceType = "docs";
      documentId = segments[5];
      resourceLink = containerLink + "/docs/" + documentId;
    } else if (segments.length == 5 && segments[4].equals("pkranges")) {
      resourceType = "pkranges";
      resourceLink = containerLink;
    } else {
      return fail(request, 404, "NotFound", "Unknown Cosmos DB resource " + request.path());
    }
    String authorizationError = cosmosAuthorizationError(request, resourceType, resourceLink);
    if (authorizationError != null) {
      return fail(request, 401, "Unauthorized", authorizationError);
    }
    Container container = containers.get(database + "/" + containerId);
    if (container == null) {
      return fail(request, 404, "NotFound", "Resource Not Found. Learn more: https://aka.ms/cosmosdb-tsg-not-found");
    }
    return switch (resourceType) {
      case "colls" -> request.method().equals("GET") ? Response.json(200, containerDefinition(container))
          : fail(request, 405, "MethodNotAllowed", "Unsupported container operation " + request.method());
      case "pkranges" -> Response.json(200, partitionKeyRanges(container));
      default -> documentId == null ? documentsFeed(request, container) : document(request, container, documentId);
    };
  }

  private static String cosmosAuthorizationError(Request request, String resourceType, String resourceLink) {
    String date = request.header("x-ms-date");
    String authorization = request.header("authorization");
    if (date == null || authorization == null) {
      return "Required headers x-ms-date and authorization are missing";
    }
    if (!"2020-07-15".equals(request.header("x-ms-version"))) {
      return "Unexpected x-ms-version " + request.header("x-ms-version");
    }
    String stringToSign = request.method().toLowerCase(Locale.ROOT) + "\n" + resourceType.toLowerCase(Locale.ROOT)
        + "\n" + resourceLink + "\n" + date.toLowerCase(Locale.ROOT) + "\n" + "" + "\n";
    String expected = "type=master&ver=1.0&sig=" + hmac(Base64.getDecoder().decode(COSMOS_KEY), stringToSign);
    String received = URLDecoder.decode(authorization, StandardCharsets.UTF_8);
    return expected.equals(received)
        ? null
        : "The input authorization token can't serve the request. The wrong key is being used or the expected payload"
            + " is not built as per the protocol. Server used the following payload to sign: '" + stringToSign + "'";
  }

  private static Map<String, Object> containerDefinition(Container container) {
    Map<String, Object> partitionKey = new LinkedHashMap<>();
    partitionKey.put("paths", List.of(container.partitionKeyPath()));
    partitionKey.put("kind", "Hash");
    partitionKey.put("version", 2);
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("id", container.id());
    definition.put("partitionKey", partitionKey);
    if (container.uniqueKeyPath() != null) {
      definition.put("uniqueKeyPolicy", Map.of("uniqueKeys", List.of(Map.of("paths", List.of(container.uniqueKeyPath())))));
    }
    definition.put("_rid", container.rid());
    definition.put("_self", "dbs/" + container.database() + "/colls/" + container.rid() + "/");
    definition.put("_etag", "\"" + container.rid() + "\"");
    return definition;
  }

  private static Map<String, Object> partitionKeyRanges(Container container) {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("id", "0");
    range.put("minInclusive", "");
    range.put("maxExclusive", "FF");
    range.put("status", "online");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("_rid", container.rid());
    body.put("PartitionKeyRanges", List.of(range));
    body.put("_count", 1);
    return body;
  }

  private Response documentsFeed(Request request, Container container) {
    if (!request.method().equals("POST")) {
      return fail(request, 405, "MethodNotAllowed", "Unsupported documents operation " + request.method());
    }
    if ("True".equals(request.header("x-ms-cosmos-is-batch-request"))) {
      return batch(request, container);
    }
    if ("True".equals(request.header("x-ms-documentdb-isquery"))) {
      return query(request, container);
    }
    Map<String, Object> body = asMap(request.json());
    if (body == null) {
      return fail(request, 400, "BadRequest", "The document is not a JSON object");
    }
    Object headerKey = partitionKeyHeader(request);
    if (headerKey == MISSING) {
      return fail(request, 400, "BadRequest", "The partition key header is required for writes");
    }
    Object documentKey = partitionKeyOf(container, body);
    if (!sameValue(headerKey, documentKey)) {
      return fail(request, 400, "BadRequest", "PartitionKey extracted from document doesn't match the one specified in"
          + " the header. Learn more: https://aka.ms/CosmosDB/sql/errors/wrong-pk-value");
    }
    boolean upsert = "True".equals(request.header("x-ms-documentdb-is-upsert"));
    WriteResult result = upsert ? upsert(container, body, request.header("if-match")) : create(container, body);
    if (result.status() >= 300) {
      return Response.error(result.status(), result.code(), result.message());
    }
    return Response.json(result.status(), result.document(), "etag", String.valueOf(result.document().get("_etag")));
  }

  private Response document(Request request, Container container, String id) {
    Object headerKey = partitionKeyHeader(request);
    if (headerKey == MISSING) {
      return fail(request, 400, "BadRequest", "The partition key header is required for document operations");
    }
    Map<String, Object> existing = find(container, id, headerKey);
    switch (request.method()) {
      case "GET" -> {
        if (existing == null) {
          return notFound();
        }
        return Response.json(200, existing, "etag", String.valueOf(existing.get("_etag")));
      }
      case "PUT" -> {
        Map<String, Object> body = asMap(request.json());
        if (existing == null) {
          return notFound();
        }
        if (body == null || !id.equals(String.valueOf(body.get("id")))) {
          return fail(request, 400, "BadRequest", "The id of the replaced document must be " + id);
        }
        Map<String, Object> stored = store(container, body, existing);
        return Response.json(200, stored, "etag", String.valueOf(stored.get("_etag")));
      }
      case "DELETE" -> {
        if (existing == null) {
          return notFound();
        }
        documents.get(key(container)).remove(existing);
        return new Response(204, Map.of(), "");
      }
      default -> {
        return fail(request, 405, "MethodNotAllowed", "Unsupported document operation " + request.method());
      }
    }
  }

  private static Response notFound() {
    return Response.error(404, "NotFound", "Entity with the specified id does not exist in the system. More info:"
        + " https://aka.ms/cosmosdb-tsg-not-found");
  }

  private record WriteResult(int status, Map<String, Object> document, String code, String message) {
    static WriteResult ok(int status, Map<String, Object> document) {
      return new WriteResult(status, document, null, null);
    }

    static WriteResult error(int status, String code, String message) {
      return new WriteResult(status, null, code, message);
    }
  }

  private WriteResult create(Container container, Map<String, Object> body) {
    Object partitionKey = partitionKeyOf(container, body);
    Object id = body.get("id");
    if (!(id instanceof String text) || text.isEmpty()) {
      return WriteResult.error(400, "BadRequest", "The input content is invalid because the required properties - 'id; '"
          + " - are missing");
    }
    if (find(container, text, partitionKey) != null) {
      return WriteResult.error(409, "Conflict", "Entity with the specified id already exists in the system.");
    }
    if (container.uniqueKeyPath() != null) {
      Object unique = valueAtPath(body, container.uniqueKeyPath());
      for (Map<String, Object> document : documents.get(key(container))) {
        if (sameValue(partitionKeyOf(container, document), partitionKey)
            && sameValue(valueAtPath(document, container.uniqueKeyPath()), unique)) {
          return WriteResult.error(409, "Conflict", "Unique index constraint violation.");
        }
      }
    }
    return WriteResult.ok(201, store(container, body, null));
  }

  private WriteResult upsert(Container container, Map<String, Object> body, String ifMatch) {
    Object id = body.get("id");
    if (!(id instanceof String text) || text.isEmpty()) {
      return WriteResult.error(400, "BadRequest", "The document has no id");
    }
    Map<String, Object> existing = find(container, text, partitionKeyOf(container, body));
    if (existing == null) {
      if (ifMatch != null && !ifMatch.equals("*")) {
        return WriteResult.error(412, "PreconditionFailed", "Operation cannot be performed because one of the specified"
            + " precondition is not met.");
      }
      return create(container, body);
    }
    if (ifMatch != null && !ifMatch.equals("*") && !ifMatch.equals(existing.get("_etag"))) {
      return WriteResult.error(412, "PreconditionFailed", "Operation cannot be performed because one of the specified"
          + " precondition is not met.");
    }
    return WriteResult.ok(200, store(container, body, existing));
  }

  private Map<String, Object> store(Container container, Map<String, Object> body, Map<String, Object> replaced) {
    long number = ++sequence;
    Map<String, Object> document = new LinkedHashMap<>();
    body.forEach((name, value) -> {
      if (!SYSTEM_PROPERTIES.contains(name)) {
        document.put(name, value);
      }
    });
    String rid = rid();
    document.put("_rid", rid);
    document.put("_self", "dbs/" + container.database() + "/colls/" + container.rid() + "/docs/" + rid + "/");
    document.put("_etag", String.format(Locale.ROOT, "\"00000000-0000-0d00-0000-%012x\"", number));
    document.put("_attachments", "attachments/");
    document.put("_ts", Instant.now().getEpochSecond());
    List<Map<String, Object>> stored = documents.get(key(container));
    if (replaced != null) {
      stored.set(stored.indexOf(replaced), document);
    } else {
      stored.add(document);
    }
    Map<String, Object> feedItem = new LinkedHashMap<>(document);
    feedItem.put("_lsn", number);
    changeFeeds.get(key(container)).add(feedItem);
    return copy(document);
  }

  private Response batch(Request request, Container container) {
    Object headerKey = partitionKeyHeader(request);
    if (headerKey == MISSING || !"True".equals(request.header("x-ms-cosmos-batch-atomic"))) {
      return fail(request, 400, "BadRequest", "A transactional batch needs a partition key and x-ms-cosmos-batch-atomic");
    }
    if (!(request.json() instanceof List<?> operations)) {
      return fail(request, 400, "BadRequest", "The batch body is not an array");
    }
    // Atomic: validate every operation against a scratch copy first
    List<Map<String, Object>> snapshot = new ArrayList<>(documents.get(key(container)));
    List<Map<String, Object>> feedSnapshot = new ArrayList<>(changeFeeds.get(key(container)));
    long sequenceSnapshot = sequence;
    List<Map<String, Object>> results = new ArrayList<>();
    int failedAt = -1;
    for (int i = 0; i < operations.size(); i++) {
      Map<String, Object> operation = asMap(operations.get(i));
      String type = operation == null ? null : String.valueOf(operation.get("operationType"));
      Map<String, Object> resource = operation == null ? null : asMap(operation.get("resourceBody"));
      WriteResult result;
      if (resource == null || !List.of("Create", "Upsert").contains(type)) {
        result = WriteResult.error(400, "BadRequest", "Unsupported batch operation " + type);
      } else if (!sameValue(partitionKeyOf(container, resource), headerKey)) {
        result = WriteResult.error(400, "BadRequest", "PartitionKey extracted from document doesn't match the batch's");
      } else {
        result = type.equals("Create") ? create(container, resource) : upsert(container, resource, null);
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("statusCode", result.status());
      entry.put("requestCharge", 6.29);
      if (result.document() != null) {
        entry.put("eTag", result.document().get("_etag"));
        entry.put("resourceBody", result.document());
      }
      results.add(entry);
      if (result.status() >= 300) {
        failedAt = i;
        break;
      }
    }
    if (failedAt >= 0) {
      documents.put(key(container), snapshot);
      changeFeeds.put(key(container), feedSnapshot);
      sequence = sequenceSnapshot;
      List<Map<String, Object>> failed = new ArrayList<>();
      for (int i = 0; i < operations.size(); i++) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("statusCode", i == failedAt ? results.get(failedAt).get("statusCode") : 424);
        entry.put("requestCharge", 0);
        failed.add(entry);
      }
      return Response.json(207, failed, "x-ms-substatus", "0");
    }
    return Response.json(200, results);
  }

  private Response query(Request request, Container container) {
    if (!"application/query+json".equals(request.header("content-type"))) {
      return fail(request, 400, "BadRequest", "Queries need the content type application/query+json");
    }
    Map<String, Object> spec = asMap(request.json());
    String text = spec == null ? null : (String) spec.get("query");
    if (text == null) {
      return fail(request, 400, "BadRequest", "The query body has no query");
    }
    Map<String, Object> parameters = new LinkedHashMap<>();
    if (spec.get("parameters") instanceof List<?> list) {
      for (Object item : list) {
        Map<String, Object> parameter = asMap(item);
        if (parameter != null) {
          parameters.put(String.valueOf(parameter.get("name")), parameter.get("value"));
        }
      }
    }
    FakeCosmosSql.Query query = FakeCosmosSql.parse(text);
    boolean crossPartition = "True".equals(request.header("x-ms-documentdb-query-enablecrosspartition"));
    if ("True".equals(request.header("x-ms-cosmos-is-query-plan-request"))) {
      return Response.json(200, FakeCosmosSql.plan(query));
    }
    Object headerKey = partitionKeyHeader(request);
    List<Map<String, Object>> scope = new ArrayList<>();
    if (headerKey != MISSING) {
      for (Map<String, Object> document : documents.get(key(container))) {
        if (sameValue(partitionKeyOf(container, document), headerKey)) {
          scope.add(document);
        }
      }
    } else if (!crossPartition) {
      return fail(request, 400, "BadRequest", "Cross partition query is required but disabled. Please set"
          + " x-ms-documentdb-query-enablecrosspartition to true");
    } else {
      if (request.header("x-ms-documentdb-partitionkeyrangeid") == null && !query.isGatewayServable()) {
        // What the gateway answers for a cross-partition query it cannot serve: the client must run the pipeline
        return fail(request, 400, "BadRequest", "The provided cross partition query can not be directly served by the"
            + " gateway. This is a first chance (internal) exception that all newer clients will know how to handle"
            + " gracefully.", "x-ms-substatus", "1004");
      }
      scope.addAll(documents.get(key(container)));
    }
    List<Object> rows = FakeCosmosSql.execute(query, copies(scope), parameters);
    int start = 0;
    String continuation = request.header("x-ms-continuation");
    if (continuation != null && continuation.startsWith("fake-skip:")) {
      start = Integer.parseInt(continuation.substring("fake-skip:".length()));
    }
    int pageSize = rows.size();
    String maxItemCount = request.header("x-ms-max-item-count");
    if (maxItemCount != null && Integer.parseInt(maxItemCount) > 0) {
      pageSize = Integer.parseInt(maxItemCount);
    }
    int end = Math.min(rows.size(), start + pageSize);
    List<Object> page = new ArrayList<>(rows.subList(Math.min(start, rows.size()), end));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("_rid", container.rid());
    body.put("Documents", page);
    body.put("_count", page.size());
    return end < rows.size()
        ? Response.json(200, body, "x-ms-item-count", String.valueOf(page.size()), "x-ms-continuation",
            "fake-skip:" + end)
        : Response.json(200, body, "x-ms-item-count", String.valueOf(page.size()));
  }

  /** The properties the service sets on every document (values sent by clients are ignored). */
  private static final java.util.Set<String> SYSTEM_PROPERTIES =
      java.util.Set.of("_rid", "_self", "_etag", "_attachments", "_ts", "_lsn");

  /** No {@code x-ms-documentdb-partitionkey} header. */
  private static final Object MISSING = new Object();

  private static Object partitionKeyHeader(Request request) {
    String header = request.header("x-ms-documentdb-partitionkey");
    if (header == null) {
      return MISSING;
    }
    Object parsed = BoosterJson.parse(header);
    if (!(parsed instanceof List<?> list) || list.size() != 1) {
      throw new IllegalArgumentException("Malformed partition key header " + header);
    }
    Object value = list.getFirst();
    // [{}] is the partition key of documents without the partition key property
    return value instanceof Map<?, ?> map && map.isEmpty() ? FakeCosmosSql.UNDEFINED : value;
  }

  private static Object partitionKeyOf(Container container, Map<String, Object> document) {
    return valueAtPath(document, container.partitionKeyPath());
  }

  private static Object valueAtPath(Map<String, Object> document, String path) {
    Object current = document;
    for (String name : path.substring(1).split("/")) {
      if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
        return FakeCosmosSql.UNDEFINED;
      }
      current = map.get(name);
    }
    return current;
  }

  private Map<String, Object> find(Container container, String id, Object partitionKey) {
    for (Map<String, Object> document : documents.get(key(container))) {
      if (id.equals(document.get("id")) && sameValue(partitionKeyOf(container, document), partitionKey)) {
        return document;
      }
    }
    return null;
  }

  /** Equality of two (possibly undefined) JSON values. */
  private static boolean sameValue(Object left, Object right) {
    if (left == FakeCosmosSql.UNDEFINED || right == FakeCosmosSql.UNDEFINED) {
      return left == right;
    }
    return Objects.equals(BoosterJson.toJson(left), BoosterJson.toJson(right));
  }

  private static String key(Container container) {
    return container.database() + "/" + container.id();
  }

  private String rid() {
    long number = ++sequence;
    byte[] bytes = new byte[8];
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (number & 0xff);
      number >>= 8;
    }
    return Base64.getEncoder().encodeToString(bytes);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Event Hubs

  private Response eventHubs(Request request) {
    String hub = request.path().substring(1, request.path().length() - "/messages".length());
    if (!request.method().equals("POST") || request.query() == null || !request.query().contains("api-version=")) {
      return fail(request, 400, "BadRequest", "Unsupported Event Hubs request " + request.summary());
    }
    String error = sasError(request.header("authorization"));
    if (error != null) {
      return fail(request, 401, "Unauthorized", error);
    }
    if (!"application/vnd.microsoft.servicebus.json".equals(request.header("content-type"))) {
      return fail(request, 400, "BadRequest", "A batch needs the content type application/vnd.microsoft.servicebus.json");
    }
    if (!(request.json() instanceof List<?> entries)) {
      return fail(request, 400, "BadRequest", "The batch body is not an array");
    }
    List<Map<String, Object>> stored = eventHubMessages.computeIfAbsent(hub, ignored -> new ArrayList<>());
    for (Object entry : entries) {
      stored.add(asMap(entry));
    }
    return new Response(201, Map.of(), "");
  }

  private static String sasError(String authorization) {
    String prefix = "SharedAccessSignature ";
    if (authorization == null || !authorization.startsWith(prefix)) {
      return "Missing shared access signature";
    }
    Map<String, String> parts = new LinkedHashMap<>();
    for (String part : authorization.substring(prefix.length()).split("&")) {
      int equals = part.indexOf('=');
      if (equals > 0) {
        parts.put(part.substring(0, equals), part.substring(equals + 1));
      }
    }
    if (!EVENT_HUBS_KEY_NAME.equals(parts.get("skn")) || parts.get("sr") == null || parts.get("se") == null) {
      return "Malformed shared access signature " + authorization;
    }
    String expected = hmac(EVENT_HUBS_KEY.getBytes(StandardCharsets.UTF_8), parts.get("sr") + "\n" + parts.get("se"));
    if (!expected.equals(URLDecoder.decode(parts.get("sig"), StandardCharsets.UTF_8))) {
      return "Invalid shared access signature";
    }
    return Long.parseLong(parts.get("se")) > Instant.now().getEpochSecond() ? null : "Expired shared access signature";
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Web PubSub

  private Response webPubSub(Request request) {
    String[] segments = request.path().substring(1).split("/");
    // api / hubs / <hub> / connections / <id> / :send
    if (!request.method().equals("POST") || segments.length != 6 || !segments[3].equals("connections")
        || !segments[5].equals(":send")) {
      return fail(request, 404, "NotFound", "Unsupported Web PubSub request " + request.summary());
    }
    String error = jwtError(request.header("authorization"));
    if (error != null) {
      return fail(request, 401, "Unauthorized", error);
    }
    webPubSubMessages.add(new WebPubSubMessage(segments[2], segments[4], request.header("content-type"),
        request.body()));
    return new Response(202, Map.of(), "");
  }

  private static String jwtError(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return "Missing access token";
    }
    String[] parts = authorization.substring("Bearer ".length()).split("\\.");
    if (parts.length != 3) {
      return "Malformed access token";
    }
    String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Base64.getDecoder().decode(hmac(WEB_PUB_SUB_KEY.getBytes(StandardCharsets.UTF_8), parts[0] + "." + parts[1])));
    return signature.equals(parts[2]) ? null : "Invalid access token signature";
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers

  private static String hmac(byte[] key, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> copy(Map<String, Object> document) {
    return (Map<String, Object>) BoosterJson.parse(BoosterJson.toJson(document));
  }

  private static List<Map<String, Object>> copies(List<Map<String, Object>> list) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> document : list) {
      result.add(copy(document));
    }
    return result;
  }
}
