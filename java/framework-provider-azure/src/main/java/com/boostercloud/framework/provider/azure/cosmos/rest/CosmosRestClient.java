package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.cosmos.BatchResponse;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosDatabase;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItem;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItems;
import com.boostercloud.framework.provider.azure.cosmos.CosmosQueryIterator;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.OperationInput;
import com.boostercloud.framework.provider.azure.cosmos.PartitionKeyValue;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.types.BoosterJson;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Production {@link CosmosClient}: the Cosmos DB (SQL API) REST API over {@code java.net.http}, authenticated with the
 * account master key (no Azure SDK).
 *
 * <p>Behaviour reproduced from {@code @azure/cosmos} (what the TypeScript adapters rely on):
 *
 * <ul>
 *   <li>{@code items.create}/{@code upsert}/{@code batch} generate a random UUID {@code id} for documents that have
 *       none (events, snapshots, dispatched markers, dedup records, subscriptions and connections rely on it);
 *   <li>the partition key of {@code create}/{@code upsert} is read from the document with the container's partition
 *       key definition (fetched once per container and cached);
 *   <li>{@code item.read()} of a missing document returns a 404 response with no resource instead of throwing;
 *   <li>throttled requests (429) are retried with {@link ResourceThrottleRetryPolicy};
 *   <li>cross-partition queries ask the gateway for a query plan; queries the gateway can serve (plain filters and
 *       projections) are fanned out by the gateway and paged with its continuation tokens, the others
 *       ({@code ORDER BY}, {@code DISTINCT}, {@code OFFSET/LIMIT}, {@code TOP}, {@code VALUE} aggregates) run against
 *       every overlapping partition key range and are merged by the client, as the SDK's query pipeline does.
 * </ul>
 */
public final class CosmosRestClient implements CosmosClient {

  static final String SUPPORTED_QUERY_FEATURES =
      "Aggregate, CompositeAggregate, Distinct, MultipleOrderBy, OffsetAndLimit, OrderBy, Top";
  static final String QUERY_VERSION = "1.4";
  static final String PIPELINE_TOKEN_PREFIX = "booster-pipeline-skip:";
  private static final int CROSS_PARTITION_QUERY_NOT_SERVABLE = 1004;

  private final CosmosRestTransport transport;
  private final Supplier<String> idGenerator;
  private final Map<String, String> partitionKeyPaths = new ConcurrentHashMap<>();

  /**
   * @param connectionString the connection string
   * @param retryOptions throttling retry options
   * @param httpClient the HTTP client
   * @param clock clock used for {@code x-ms-date}
   * @param sleeper used to wait between throttling retries
   * @param idGenerator generates document ids ({@code UUID.randomUUID().toString()} in production)
   */
  public CosmosRestClient(
      CosmosConnectionString connectionString,
      CosmosRetryOptions retryOptions,
      HttpClient httpClient,
      Clock clock,
      Retrier.Sleeper sleeper,
      Supplier<String> idGenerator) {
    this.transport = new CosmosRestTransport(connectionString, retryOptions, httpClient, clock, sleeper);
    this.idGenerator = idGenerator;
  }

  /**
   * Production client for a connection string, with the retry options of the {@code COSMOSDB_*} app settings.
   *
   * @param connectionString {@code AccountEndpoint=...;AccountKey=...;}
   * @param retryOptions throttling retry options
   * @return the client
   */
  public static CosmosRestClient create(String connectionString, CosmosRetryOptions retryOptions) {
    return new CosmosRestClient(
        CosmosConnectionString.parse(connectionString),
        retryOptions,
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        Clock.systemUTC(),
        Retrier.Sleeper.THREAD_SLEEP,
        () -> UUID.randomUUID().toString());
  }

  @Override
  public CosmosDatabase database(String databaseId) {
    return new RestDatabase(databaseId);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Resource handles

  private final class RestDatabase implements CosmosDatabase {
    private final String id;

    RestDatabase(String id) {
      this.id = Objects.requireNonNull(id, "databaseId");
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String url() {
      return "dbs/" + id;
    }

    @Override
    public CosmosContainer container(String containerId) {
      return new RestContainer(this, containerId);
    }
  }

  private final class RestContainer implements CosmosContainer {
    private final RestDatabase database;
    private final String id;

    RestContainer(RestDatabase database, String id) {
      this.database = database;
      this.id = Objects.requireNonNull(id, "containerId");
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String url() {
      return database.url() + "/colls/" + id;
    }

    String path() {
      return "dbs/" + segment(database.id) + "/colls/" + segment(id);
    }

    @Override
    public CosmosItems items() {
      return new RestItems(this);
    }

    @Override
    public CosmosItem item(String itemId, Object partitionKeyValue) {
      return new RestItem(this, itemId, partitionKeyValue);
    }

    /** Partition key path ({@code /id}) from the container definition, cached per container. */
    String partitionKeyPath() {
      return partitionKeyPaths.computeIfAbsent(url(), link -> {
        CosmosRestTransport.Response response = transport.send(
            new CosmosRestTransport.Request("GET", "colls", link, path(), Map.of(), null), Set.of());
        Object paths = valueAt(response.json(), "partitionKey", "paths");
        if (paths instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
          return list.getFirst().toString();
        }
        throw new IllegalStateException("The container " + link + " has no partition key definition");
      });
    }
  }

  private final class RestItems implements CosmosItems {
    private final RestContainer container;

    RestItems(RestContainer container) {
      this.container = container;
    }

    @Override
    public CosmosQueryIterator query(SqlQuerySpec querySpec, FeedOptions options) {
      return new RestQueryIterator(container, querySpec, options == null ? FeedOptions.none() : options);
    }

    @Override
    public ItemResponse create(Map<String, Object> body) {
      Map<String, Object> document = withId(body);
      Object partitionKey = extractPartitionKey(document, container.partitionKeyPath());
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "POST", "docs", container.url(), container.path() + "/docs",
          CosmosRestTransport.headers(
              "x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey),
              "Content-Type", "application/json"),
          BoosterJson.toJson(document)), Set.of());
      return itemResponse(response);
    }

    @Override
    public ItemResponse upsert(Map<String, Object> body, RequestOptions options) {
      Map<String, Object> document = withId(body);
      Object partitionKey = extractPartitionKey(document, container.partitionKeyPath());
      Map<String, String> headers = CosmosRestTransport.headers(
          "x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey),
          "x-ms-documentdb-is-upsert", "True",
          "Content-Type", "application/json");
      headers.putAll(requestOptionHeaders(options));
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "POST", "docs", container.url(), container.path() + "/docs", headers, BoosterJson.toJson(document)),
          Set.of());
      return itemResponse(response);
    }

    @Override
    public BatchResponse batch(List<OperationInput> operations, Object partitionKey, RequestOptions options) {
      List<Map<String, Object>> body = new ArrayList<>();
      for (OperationInput operation : operations) {
        Map<String, Object> rawOperation = new LinkedHashMap<>();
        rawOperation.put("operationType", operation.operationType());
        if (operation.resourceBody() != null) {
          boolean write = operation.operationType().equals("Create") || operation.operationType().equals("Upsert");
          rawOperation.put("resourceBody", write ? withId(operation.resourceBody()) : operation.resourceBody());
        }
        body.add(rawOperation);
      }
      Map<String, String> headers = CosmosRestTransport.headers(
          "x-ms-cosmos-is-batch-request", "True",
          "x-ms-cosmos-batch-atomic", "True",
          "x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey),
          "Content-Type", "application/json");
      headers.putAll(requestOptionHeaders(options));
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "POST", "docs", container.url(), container.path() + "/docs", headers, BoosterJson.toJson(body)), Set.of());
      List<BatchResponse.OperationResponse> results = new ArrayList<>();
      if (response.json() instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Map<?, ?> result) {
            results.add(new BatchResponse.OperationResponse(
                result.get("statusCode") instanceof Number n ? n.intValue() : 0,
                result.get("requestCharge") instanceof Number n ? n.doubleValue() : null,
                result.get("eTag") == null ? null : result.get("eTag").toString(),
                castMap(result.get("resourceBody"))));
          }
        }
      }
      return new BatchResponse(response.headers(), results, response.status(),
          CosmosRestTransport.parseInteger(response.header("x-ms-substatus")));
    }
  }

  private final class RestItem implements CosmosItem {
    private final RestContainer container;
    private final String id;
    private final Object partitionKey;

    RestItem(RestContainer container, String id, Object partitionKey) {
      this.container = container;
      this.id = Objects.requireNonNull(id, "id");
      this.partitionKey = partitionKey;
    }

    private String link() {
      return container.url() + "/docs/" + id;
    }

    private String path() {
      return container.path() + "/docs/" + segment(id);
    }

    @Override
    public ItemResponse read() {
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "GET", "docs", link(), path(),
          CosmosRestTransport.headers("x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey)), null),
          Set.of(404));
      if (response.status() == 404) {
        return new ItemResponse(404, null, null, response.headers());
      }
      return itemResponse(response);
    }

    @Override
    public ItemResponse replace(Map<String, Object> body) {
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "PUT", "docs", link(), path(),
          CosmosRestTransport.headers(
              "x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey),
              "Content-Type", "application/json"),
          BoosterJson.toJson(body)), Set.of());
      return itemResponse(response);
    }

    @Override
    public ItemResponse delete() {
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "DELETE", "docs", link(), path(),
          CosmosRestTransport.headers("x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey)), null),
          Set.of());
      return new ItemResponse(response.status(), null, response.header("etag"), response.headers());
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Queries

  private enum Strategy { SINGLE_PARTITION, GATEWAY, PIPELINE }

  private final class RestQueryIterator implements CosmosQueryIterator {
    private final RestContainer container;
    private final SqlQuerySpec querySpec;
    private final Integer maxItemCount;
    private final Object partitionKey;
    private String continuation;
    private boolean started;
    private boolean finished;
    private Strategy strategy;
    private CosmosQueryPlan plan;

    RestQueryIterator(RestContainer container, SqlQuerySpec querySpec, FeedOptions options) {
      this.container = container;
      this.querySpec = querySpec;
      this.maxItemCount = options.maxItemCount();
      this.partitionKey = options.partitionKey();
      this.continuation = options.continuationToken();
    }

    @Override
    public boolean hasMoreResults() {
      return !finished;
    }

    @Override
    public com.boostercloud.framework.provider.azure.cosmos.FeedResponse fetchAll() {
      if (strategy() != Strategy.PIPELINE) {
        try {
          List<Object> all = new ArrayList<>();
          Map<String, String> lastHeaders = Map.of();
          do {
            Page page = directPage(null);
            all.addAll(page.items());
            lastHeaders = page.headers();
            continuation = page.continuation();
          } while (continuation != null);
          finished = true;
          return new com.boostercloud.framework.provider.azure.cosmos.FeedResponse(all, null, false, lastHeaders);
        } catch (GatewayCannotServe fallback) {
          strategy = Strategy.PIPELINE;
          continuation = null;
        }
      }
      List<Object> all = runPipeline();
      finished = true;
      return com.boostercloud.framework.provider.azure.cosmos.FeedResponse.of(skip(all, pipelineSkip()));
    }

    @Override
    public com.boostercloud.framework.provider.azure.cosmos.FeedResponse fetchNext() {
      if (finished) {
        return com.boostercloud.framework.provider.azure.cosmos.FeedResponse.of(List.of());
      }
      if (strategy() != Strategy.PIPELINE) {
        try {
          List<Object> items = new ArrayList<>();
          Map<String, String> lastHeaders = Map.of();
          do {
            Integer requested = maxItemCount == null || maxItemCount <= 0 ? maxItemCount : maxItemCount - items.size();
            Page page = directPage(requested);
            items.addAll(page.items());
            lastHeaders = page.headers();
            continuation = page.continuation();
          } while (continuation != null && maxItemCount != null && maxItemCount > 0 && items.size() < maxItemCount);
          finished = continuation == null;
          return new com.boostercloud.framework.provider.azure.cosmos.FeedResponse(
              items, continuation, continuation != null, lastHeaders);
        } catch (GatewayCannotServe fallback) {
          strategy = Strategy.PIPELINE;
          continuation = null;
        }
      }
      List<Object> all = runPipeline();
      int skip = pipelineSkip();
      List<Object> remaining = skip(all, skip);
      int pageSize = maxItemCount == null || maxItemCount < 0 ? remaining.size() : maxItemCount;
      List<Object> page = new ArrayList<>(remaining.subList(0, Math.min(pageSize, remaining.size())));
      boolean more = remaining.size() > page.size();
      continuation = more ? PIPELINE_TOKEN_PREFIX + (skip + page.size()) : null;
      finished = !more;
      return new com.boostercloud.framework.provider.azure.cosmos.FeedResponse(page, continuation, more, Map.of());
    }

    private Strategy strategy() {
      if (!started) {
        started = true;
        if (partitionKey != null) {
          strategy = Strategy.SINGLE_PARTITION;
        } else if (continuation != null && continuation.startsWith(PIPELINE_TOKEN_PREFIX)) {
          plan = fetchQueryPlan();
          strategy = Strategy.PIPELINE;
        } else {
          plan = fetchQueryPlan();
          strategy = plan.isGatewayServable() ? Strategy.GATEWAY : Strategy.PIPELINE;
        }
      }
      return strategy;
    }

    private int pipelineSkip() {
      if (continuation != null && continuation.startsWith(PIPELINE_TOKEN_PREFIX)) {
        try {
          return Integer.parseInt(continuation.substring(PIPELINE_TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid continuation token: " + continuation, e);
        }
      }
      return 0;
    }

    private Page directPage(Integer pageSize) {
      Map<String, String> headers = queryHeaders();
      if (strategy == Strategy.SINGLE_PARTITION) {
        headers.put("x-ms-documentdb-partitionkey", partitionKeyHeader(partitionKey));
      } else {
        headers.put("x-ms-documentdb-query-enablecrosspartition", "True");
      }
      if (pageSize != null) {
        headers.put("x-ms-max-item-count", pageSize.toString());
      }
      if (continuation != null) {
        headers.put("x-ms-continuation", continuation);
      }
      try {
        return queryPage(querySpec, headers);
      } catch (CosmosException e) {
        if (strategy == Strategy.GATEWAY && e.code() == 400
            && Objects.equals(e.substatus(), CROSS_PARTITION_QUERY_NOT_SERVABLE)) {
          // The gateway refused to serve the query itself: the caller falls back to the client-side pipeline.
          throw new GatewayCannotServe(e);
        }
        throw e;
      }
    }

    private CosmosQueryPlan fetchQueryPlan() {
      Map<String, String> headers = queryHeaders();
      headers.put("x-ms-cosmos-is-query-plan-request", "True");
      headers.put("x-ms-cosmos-supported-query-features", SUPPORTED_QUERY_FEATURES);
      headers.put("x-ms-cosmos-query-version", QUERY_VERSION);
      headers.put("x-ms-documentdb-query-enablecrosspartition", "True");
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "POST", "docs", container.url(), container.path() + "/docs", headers, BoosterJson.toJson(querySpec)),
          Set.of());
      return CosmosQueryPlan.fromJson(response.json());
    }

    private List<Object> runPipeline() {
      if (plan == null) {
        plan = fetchQueryPlan();
      }
      if (!plan.groupByExpressions().isEmpty()) {
        throw new UnsupportedOperationException(
            "Cross-partition GROUP BY queries are not supported by the Booster Cosmos DB REST client: "
                + querySpec.query());
      }
      if (!plan.aggregates().isEmpty() && !plan.hasSelectValue()) {
        throw new UnsupportedOperationException(
            "Cross-partition aggregate queries must use SELECT VALUE with the Booster Cosmos DB REST client: "
                + querySpec.query());
      }
      SqlQuerySpec rangeQuery = querySpec.withQuery(plan.queryForRanges(querySpec.query()));
      List<List<Object>> perRange = new ArrayList<>();
      for (String rangeId : overlappingRangeIds(plan)) {
        List<Object> results = new ArrayList<>();
        String rangeContinuation = null;
        do {
          Map<String, String> headers = queryHeaders();
          headers.put("x-ms-documentdb-query-enablecrosspartition", "True");
          headers.put("x-ms-documentdb-partitionkeyrangeid", rangeId);
          if (rangeContinuation != null) {
            headers.put("x-ms-continuation", rangeContinuation);
          }
          Page page = queryPage(rangeQuery, headers);
          results.addAll(page.items());
          rangeContinuation = page.continuation();
        } while (rangeContinuation != null);
        perRange.add(results);
      }
      return CosmosQueryPipeline.combine(plan, perRange);
    }

    private List<String> overlappingRangeIds(CosmosQueryPlan queryPlan) {
      List<String> ids = new ArrayList<>();
      String rangesContinuation = null;
      do {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rangesContinuation != null) {
          headers.put("x-ms-continuation", rangesContinuation);
        }
        CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
            "GET", "pkranges", container.url(), container.path() + "/pkranges", headers, null), Set.of());
        Object ranges = valueAt(response.json(), "PartitionKeyRanges");
        if (ranges instanceof List<?> list) {
          for (Object item : list) {
            if (item instanceof Map<?, ?> range) {
              String min = String.valueOf(range.get("minInclusive"));
              String max = String.valueOf(range.get("maxExclusive"));
              if (queryPlan.queryRanges().stream().anyMatch(queryRange -> queryRange.overlaps(min, max))) {
                ids.add(String.valueOf(range.get("id")));
              }
            }
          }
        }
        rangesContinuation = response.header("x-ms-continuation");
      } while (rangesContinuation != null);
      return ids;
    }

    private Map<String, String> queryHeaders() {
      return CosmosRestTransport.headers(
          "x-ms-documentdb-isquery", "True",
          "Content-Type", "application/query+json");
    }

    private Page queryPage(SqlQuerySpec spec, Map<String, String> headers) {
      CosmosRestTransport.Response response = transport.send(new CosmosRestTransport.Request(
          "POST", "docs", container.url(), container.path() + "/docs", headers, BoosterJson.toJson(spec)), Set.of());
      List<Object> documents = new ArrayList<>();
      if (valueAt(response.json(), "Documents") instanceof List<?> list) {
        documents.addAll(list);
      }
      String next = response.header("x-ms-continuation");
      return new Page(documents, next == null || next.isEmpty() ? null : next, response.headers());
    }
  }

  private record Page(List<Object> items, String continuation, Map<String, String> headers) {}

  /** The gateway answered 400/1004 ("cannot be directly served by the gateway"). */
  private static final class GatewayCannotServe extends RuntimeException {
    private static final long serialVersionUID = 1L;

    GatewayCannotServe(CosmosException cause) {
      super(cause.getMessage(), cause, false, false);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers

  private Map<String, Object> withId(Map<String, Object> body) {
    Map<String, Object> document = new LinkedHashMap<>(body);
    Object id = document.get("id");
    if (id == null || "".equals(id)) {
      document.remove("id");
      document.put("id", idGenerator.get());
    }
    return document;
  }

  private static ItemResponse itemResponse(CosmosRestTransport.Response response) {
    return new ItemResponse(response.status(), castMap(response.json()), response.header("etag"), response.headers());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  private static List<Object> skip(List<Object> items, int count) {
    return count <= 0 ? items : items.subList(Math.min(count, items.size()), items.size());
  }

  static Object valueAt(Object root, String... path) {
    Object current = root;
    for (String key : path) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(key);
    }
    return current;
  }

  /**
   * Reads the partition key value of a document ({@code /a/b} reads {@code document.a.b}).
   *
   * @param document the document
   * @param path the partition key path
   * @return the value, or {@link PartitionKeyValue#UNDEFINED} when the property is absent
   */
  static Object extractPartitionKey(Map<String, Object> document, String path) {
    Object current = document;
    for (String segment : path.substring(path.startsWith("/") ? 1 : 0).split("/")) {
      String key = segment.replace("~1", "/").replace("~0", "~");
      if (!(current instanceof Map<?, ?> map) || !map.containsKey(key)) {
        return PartitionKeyValue.UNDEFINED;
      }
      current = map.get(key);
    }
    return current;
  }

  /**
   * @param value a partition key value
   * @return the {@code x-ms-documentdb-partitionkey} header: a JSON array with the value, {@code [{}]} when undefined.
   *     Like the SDK's {@code jsonStringifyAndEscapeNonASCII}, every UTF-16 unit from U+007F up is written as a JSON
   *     unicode escape ({@link #escapeNonAscii}), so the header is ASCII (the JDK client rejects header values above
   *     U+00FF and would send U+0080 to U+00FF as single ISO-8859-1 bytes)
   */
  static String partitionKeyHeader(Object value) {
    if (value == PartitionKeyValue.UNDEFINED) {
      return "[{}]";
    }
    return escapeNonAscii(BoosterJson.toJson(java.util.Collections.singletonList(value)));
  }

  /**
   * {@code jsonStringifyAndEscapeNonASCII}'s replacement step: every UTF-16 unit from U+007F to U+FFFF becomes a
   * backslash, {@code u} and its code as four lower-case hexadecimal digits. Outside strings JSON text is ASCII, so the
   * result is the same JSON value.
   *
   * @param json JSON text
   * @return the text with every character from U+007F up escaped
   */
  static String escapeNonAscii(String json) {
    StringBuilder escaped = null;
    for (int i = 0; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c >= 0x7F) {
        if (escaped == null) {
          escaped = new StringBuilder(json.length() + 16).append(json, 0, i);
        }
        escaped.append(String.format("\\u%04x", (int) c));
      } else if (escaped != null) {
        escaped.append(c);
      }
    }
    return escaped == null ? json : escaped.toString();
  }

  static Map<String, String> requestOptionHeaders(RequestOptions options) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (options == null) {
      return headers;
    }
    if (options.accessCondition() != null && options.accessCondition().condition() != null) {
      String header = "IfNoneMatch".equals(options.accessCondition().type()) ? "If-None-Match" : "If-Match";
      headers.put(header, options.accessCondition().condition());
    }
    if (options.consistencyLevel() != null) {
      headers.put("x-ms-consistency-level", options.consistencyLevel());
    }
    if (options.disableRUPerMinuteUsage() != null) {
      headers.put("x-ms-documentdb-disable-ru-per-minute-usage", options.disableRUPerMinuteUsage().toString());
    }
    if (options.indexingDirective() != null) {
      headers.put("x-ms-indexing-directive", options.indexingDirective());
    }
    return headers;
  }

  /**
   * @param value a resource id
   * @return the id percent-encoded as a URL path segment
   */
  static String segment(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
