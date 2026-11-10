package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.provider.local.services.ReadModelRegistry;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Health sensor support of the local provider. Port of {@code library/health-adapter.ts}.
 */
public final class HealthAdapter {

  private HealthAdapter() {}

  /**
   * {@code databaseUrl()}.
   *
   * @return {@code ['.booster/events.json', '.booster/read_models.json']}
   */
  public static List<String> databaseUrl() {
    return List.of(Paths.EVENTS_DATABASE, Paths.READ_MODELS_DATABASE);
  }

  /**
   * {@code countAll(database)}: the number of documents (TypeScript reloads the file from disk first; every write of
   * this store is already on disk, so the loaded data is used).
   *
   * @param database the collection
   * @return the number of documents
   */
  public static int countAll(Datastore database) {
    database.loadDatabaseIfNeeded();
    return database.count(Map.of());
  }

  /**
   * {@code databaseEventsHealthDetails(eventRegistry)}.
   *
   * @param eventRegistry the registry
   * @return {@code { file: '.booster/events.json', count }} (events and snapshots, including soft-deleted events)
   */
  public static Map<String, Object> databaseEventsHealthDetails(EventRegistry eventRegistry) {
    int count = countAll(eventRegistry.events());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("file", Paths.EVENTS_DATABASE);
    details.put("count", count);
    return details;
  }

  /**
   * {@code databaseReadModelsHealthDetails(readModelRegistry)}.
   *
   * @param readModelRegistry the registry
   * @return {@code { file: '.booster/read_models.json', count }} (read models of every type)
   */
  public static Map<String, Object> databaseReadModelsHealthDetails(ReadModelRegistry readModelRegistry) {
    int count = countAll(readModelRegistry.readModels());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("file", Paths.READ_MODELS_DATABASE);
    details.put("count", count);
    return details;
  }

  /**
   * {@code graphqlFunctionUrl()}.
   *
   * @return {@code http://localhost:<BOOSTER_INTERNAL_LOCAL_PORT or 3000>/graphql}, or {@code ""} when that fails
   */
  public static String graphqlFunctionUrl() {
    try {
      return "http://localhost:" + InternalInfo.boosterLocalPort() + "/graphql";
    } catch (RuntimeException e) {
      return "";
    }
  }

  /**
   * {@code isDatabaseEventUp()}: whether the events file exists (it is created on the first event operation).
   *
   * @param baseDirectory the project directory, or {@code null} for the working directory
   * @return whether it exists
   */
  public static boolean isDatabaseEventUp(Path baseDirectory) {
    return Files.exists(Paths.resolve(baseDirectory, Paths.EVENTS_DATABASE));
  }

  /**
   * {@code areDatabaseReadModelsUp()}: whether the read models file exists.
   *
   * @param baseDirectory the project directory, or {@code null} for the working directory
   * @return whether it exists
   */
  public static boolean areDatabaseReadModelsUp(Path baseDirectory) {
    return Files.exists(Paths.resolve(baseDirectory, Paths.READ_MODELS_DATABASE));
  }

  /**
   * {@code isGraphQLFunctionUp()}: POSTs {@code { query: 'query { __typename }' }} to {@link #graphqlFunctionUrl()}.
   *
   * @return whether it answered 200 (any failure is {@code false})
   */
  public static boolean isGraphQLFunctionUp() {
    try {
      String url = graphqlFunctionUrl();
      HttpService.PostResult response = HttpService.request(url, HttpService.Method.POST,
          BoosterJson.toJson(Map.of("query", "query { __typename }")));
      return response.status() == 200;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String rawRequestToSensorHealthComponentPath(LocalHttpRequest rawRequest) {
    String url = rawRequest == null ? null : rawRequest.url();
    if (url != null && !url.isEmpty() && !url.equals("/")) {
      return url.substring(1);
    }
    return "";
  }

  /**
   * {@code rawRequestToSensorHealth(rawRequest)}: the component path is the URL relative to the health endpoint
   * without its leading slash ({@code ''} for the root); the token is the {@code authorization} header; the body is
   * always {@code {}}.
   *
   * @param rawRequest a {@link LocalHttpRequest} (or a map with {@code url} and {@code headers})
   * @return the envelope
   */
  public static HealthEnvelope rawRequestToSensorHealth(Object rawRequest) {
    LocalHttpRequest request = toHttpRequest(rawRequest);
    String componentPath = rawRequestToSensorHealthComponentPath(request);
    UUID requestID = UUID.generate();
    Map<String, String> headers = request.headers();
    return HealthEnvelope.builder()
        .requestID(requestID)
        .context(ContextEnvelope.of(headers, new LinkedHashMap<String, Object>(), rawRequest))
        .componentPath(componentPath)
        .token(headers.get("authorization"))
        .build();
  }

  @SuppressWarnings("unchecked")
  static LocalHttpRequest toHttpRequest(Object rawRequest) {
    if (rawRequest instanceof LocalHttpRequest request) {
      return request;
    }
    if (rawRequest instanceof Map<?, ?> map) {
      Map<String, String> headers = new LinkedHashMap<>();
      if (map.get("headers") instanceof Map<?, ?> rawHeaders) {
        rawHeaders.forEach((name, value) -> headers.put(String.valueOf(name), value == null ? null : Js.toJsString(value)));
      }
      Object url = map.get("url");
      Object method = map.get("method");
      return LocalHttpRequest.of(method == null ? null : String.valueOf(method), url == null ? null : String.valueOf(url),
          headers, map.get("body"));
    }
    throw new IllegalArgumentException("Unsupported raw request for the local provider: "
        + (rawRequest == null ? "undefined" : rawRequest.getClass().getName()));
  }

  /**
   * {@code areRocketFunctionsUp(config)}: every rocket with a {@code rocketProviderPackage} parameter is up (rockets
   * run in the application process). The key is the package without its npm scope and last {@code -segment}
   * ({@code @boostercloud/rocket-file-uploads-local} gives {@code rocket-file-uploads}).
   *
   * @param config the configuration
   * @return package to {@code true}
   */
  public static Map<String, Boolean> areRocketFunctionsUp(BoosterConfig config) {
    Map<String, Boolean> results = new LinkedHashMap<>();
    if (config.rockets != null) {
      for (RocketDescriptor rocket : config.rockets) {
        Object parameters = rocket.parameters();
        if (parameters != null && !(parameters instanceof Map<?, ?>)) {
          try {
            parameters = BoosterJson.toRaw(parameters);
          } catch (RuntimeException e) {
            parameters = null;
          }
        }
        Object rocketProviderPackage = parameters instanceof Map<?, ?> map ? map.get("rocketProviderPackage") : null;
        if (Js.truthy(rocketProviderPackage)) {
          String basePackage = Js.toJsString(rocketProviderPackage)
              .replaceFirst("^@[^/]+/", "")
              .replaceFirst("-[^-]+$", "");
          results.put(basePackage, true);
        }
      }
    }
    return results;
  }
}
