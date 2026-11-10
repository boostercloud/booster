package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.types.AzureFunctionTypes;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Port of {@code library/health-adapter.ts}: the sensor checks (Cosmos containers, GraphQL endpoint, rocket function
 * apps) and the health request envelope.
 */
public final class HealthAdapter {

  private static final Pattern COMPONENT_PATH_PREFIX = Pattern.compile("^.*sensor/health/?");

  /** The HTTP call of the GraphQL and rocket checks ({@code request(url, method, data)} of common-helpers). */
  @FunctionalInterface
  public interface HttpRequester {
    /** Production requester. */
    HttpRequester DEFAULT = HttpService::request;

    /**
     * @param url the URL
     * @param method the method
     * @param data the body, or {@code null}
     * @return the result
     * @throws RuntimeException on non-2xx responses, timeouts and connection failures
     */
    HttpService.PostResult request(String url, HttpService.Method method, String data);
  }

  private HealthAdapter() {}

  /**
   * {@code databaseUrl}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @return {@code [dbs/<app>-app]}
   */
  public static List<String> databaseUrl(CosmosClient cosmosDb, BoosterConfig config) {
    return List.of(cosmosDb.database(config.resourceNames().applicationStack()).url());
  }

  /**
   * {@code getContainer}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param containerName the container
   * @return the handle
   */
  public static CosmosContainer getContainer(CosmosClient cosmosDb, BoosterConfig config, String containerName) {
    return cosmosDb.database(config.resourceNames().applicationStack()).container(containerName);
  }

  /**
   * {@code isContainerUp}: runs {@code SELECT TOP 1 1 FROM c}; errors are not caught.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param containerName the container
   * @return whether the query returned a result set
   */
  public static boolean isContainerUp(CosmosClient cosmosDb, BoosterConfig config, String containerName) {
    FeedResponse response = getContainer(cosmosDb, config, containerName).items()
        .query(new SqlQuerySpec("SELECT TOP 1 1 FROM c", List.of()))
        .fetchAll();
    return response != null && response.resources() != null;
  }

  /**
   * {@code countAll}: {@code SELECT VALUE COUNT(1) FROM c}.
   *
   * @param container the container
   * @return the first result ({@code null} for an empty result, like TypeScript's {@code resources[0]})
   */
  public static Object countAll(CosmosContainer container) {
    FeedResponse response = container.items().query(new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c", List.of()))
        .fetchAll();
    if (response == null || response.resources() == null) {
      return 0;
    }
    return response.resources().isEmpty() ? null : response.resources().getFirst();
  }

  /**
   * {@code databaseEventsHealthDetails}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @return {@code { url, count }} of the events container
   */
  public static Object databaseEventsHealthDetails(CosmosClient cosmosDb, BoosterConfig config) {
    CosmosContainer container = getContainer(cosmosDb, config, config.resourceNames().eventsStore());
    return details(container.url(), countAll(container));
  }

  /**
   * {@code databaseReadModelsHealthDetails}: {@code { url, count }} of every read model container, sequentially.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @return the details
   */
  public static Object databaseReadModelsHealthDetails(CosmosClient cosmosDb, BoosterConfig config) {
    List<Object> result = new ArrayList<>();
    for (ReadModelMetadata readModel : config.readModels.values()) {
      String containerName = config.resourceNames().forReadModel(readModel.type().getSimpleName());
      CosmosContainer container = getContainer(cosmosDb, config, containerName);
      result.add(details(container.url(), countAll(container)));
    }
    return result;
  }

  /**
   * {@code graphqlFunctionUrl}: {@code `${BOOSTER_REST_API_URL}/graphql`} ({@code undefined/graphql} when unset).
   *
   * @return the URL
   */
  public static String graphqlFunctionUrl() {
    return EnvironmentVariables.get(EnvironmentVarNames.REST_API_URL).orElse("undefined") + "/graphql";
  }

  /**
   * {@code rocketFunctionAppUrl}.
   *
   * @param functionAppName the function app
   * @return {@code https://<name>.azurewebsites.net}
   */
  public static String rocketFunctionAppUrl(String functionAppName) {
    return "https://" + functionAppName + ".azurewebsites.net";
  }

  /**
   * {@code isDatabaseEventUp}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @return whether the events container answers
   */
  public static boolean isDatabaseEventUp(CosmosClient cosmosDb, BoosterConfig config) {
    return isContainerUp(cosmosDb, config, config.resourceNames().eventsStore());
  }

  /**
   * {@code areDatabaseReadModelsUp}: every read model container, in parallel, fail-fast ({@code Promise.all}).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @return whether all are up (vacuously true)
   */
  public static boolean areDatabaseReadModelsUp(CosmosClient cosmosDb, BoosterConfig config) {
    List<Callable<Boolean>> checks = new ArrayList<>();
    for (ReadModelMetadata readModel : config.readModels.values()) {
      String container = config.resourceNames().forReadModel(readModel.type().getSimpleName());
      checks.add(() -> isContainerUp(cosmosDb, config, container));
    }
    return Promises.runAll(checks).stream().allMatch(Boolean::booleanValue);
  }

  /**
   * {@code isGraphQLFunctionUp}: POSTs {@code {"query":"query { __typename }"}} to the GraphQL URL; any error is
   * {@code false}.
   *
   * @param http the HTTP requester
   * @return whether it answered 200
   */
  public static boolean isGraphQLFunctionUp(HttpRequester http) {
    try {
      String restAPIUrl = graphqlFunctionUrl();
      HttpService.PostResult response =
          http.request(restAPIUrl, HttpService.Method.POST, BoosterJson.toJson(Map.of("query", "query { __typename }")));
      return response.status() == 200;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * {@code isRocketFunctionUp}: GETs the function app root; any error is {@code false}.
   *
   * @param http the HTTP requester
   * @param rocketFunctionAppName the function app
   * @return whether it answered 200
   */
  public static boolean isRocketFunctionUp(HttpRequester http, String rocketFunctionAppName) {
    try {
      HttpService.PostResult response =
          http.request(rocketFunctionAppUrl(rocketFunctionAppName), HttpService.Method.GET, null);
      return response.status() == 200;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * {@code areRocketFunctionsUp}: parses {@code ROCKET_PACKAGE_MAPPING} ({@code pkgA:appA;pkgB:appB}; empty parts and
   * pairs with an empty side are dropped, later duplicates overwrite) and checks every function app in parallel.
   *
   * @param http the HTTP requester
   * @return package name to "is up", in mapping order ({@code {}} without mapping)
   */
  public static Map<String, Boolean> areRocketFunctionsUp(HttpRequester http) {
    String mappingString = EnvironmentVariables.getOrDefault(EnvironmentVarNames.ROCKET_PACKAGE_MAPPING, "");
    Map<String, String> rocketPackageMapping = new LinkedHashMap<>();
    for (String pair : mappingString.split(";", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      String[] parts = pair.split(":", -1);
      String pkg = parts[0];
      String func = parts.length > 1 ? parts[1] : "";
      if (!pkg.isEmpty() && !func.isEmpty()) {
        rocketPackageMapping.put(pkg, func);
      }
    }
    List<Map.Entry<String, String>> entries = new ArrayList<>(rocketPackageMapping.entrySet());
    List<Callable<Boolean>> checks = new ArrayList<>();
    for (Map.Entry<String, String> entry : entries) {
      checks.add(() -> isRocketFunctionUp(http, entry.getValue()));
    }
    List<Boolean> results = Promises.runAll(checks);
    Map<String, Boolean> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.size(); i++) {
      result.put(entries.get(i).getKey(), results.get(i));
    }
    return result;
  }

  /**
   * {@code rawRequestToSensorHealthComponentPath}: the URL without everything up to {@code sensor/health/} (a URL
   * without it is returned whole, the query string is kept).
   *
   * @param rawRequest an HTTP input
   * @return the component path, or {@code ""} for other inputs
   */
  public static String rawRequestToSensorHealthComponentPath(Object rawRequest) {
    if (AzureFunctionTypes.isHttpFunctionInput(rawRequest)) {
      Object url = get(get(rawRequest, "request"), "url");
      return COMPONENT_PATH_PREFIX.matcher(url == null ? "undefined" : url.toString()).replaceFirst("");
    }
    return "";
  }

  /**
   * {@code rawRequestToSensorHealth}.
   *
   * @param rawRequest an HTTP input
   * @return {@code { requestID: invocationId, context: { request: { headers, body: undefined }, rawContext },
   *     componentPath, token: authorization ?? undefined }}
   * @throws IllegalStateException for other inputs
   */
  public static HealthEnvelope rawRequestToSensorHealth(Object rawRequest) {
    if (!AzureFunctionTypes.isHttpFunctionInput(rawRequest)) {
      throw new IllegalStateException(
          "Invalid input type for rawRequestToSensorHealth: expected AzureHttpFunctionInput");
    }
    Object rawHttpRequest = get(rawRequest, "request");
    AzureHttpRequest request = rawHttpRequest instanceof AzureHttpRequest typed
        ? typed
        : BoosterJson.convert(rawHttpRequest, AzureHttpRequest.class);
    String componentPath = rawRequestToSensorHealthComponentPath(rawRequest);
    Object invocationId = get(get(rawRequest, "context"), "invocationId");

    // Convert headers to a plain object
    Map<String, String> headers = new LinkedHashMap<>();
    request.forEachHeader((value, key) -> headers.put(key, value));

    return HealthEnvelope.builder()
        .requestID(invocationId == null ? null : UUID.of(invocationId.toString()))
        .context(ContextEnvelope.of(headers, null, rawRequest))
        .componentPath(componentPath)
        .token(request.header("authorization"))
        .build();
  }

  private static Map<String, Object> details(String url, Object count) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("url", url);
    if (count != null) {
      details.put("count", count);
    }
    return details;
  }
}
