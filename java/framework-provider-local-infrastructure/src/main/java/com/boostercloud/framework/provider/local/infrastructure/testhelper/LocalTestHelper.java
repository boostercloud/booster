package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.provider.local.library.WebSocketServerAdapter;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * What the integration tests use to reach a local application: its URLs, counters and raw queries. Port of
 * {@code test-helper/local-test-helper.ts}; it has the parts of the application tester's {@code ProviderTestHelper}
 * ({@code outputs}, {@code counters}, {@code queries}).
 *
 * <p>Differences from TypeScript: the ports come from {@code BOOSTER_INTERNAL_LOCAL_PORT} (default 3000) and
 * {@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT} (default 65529) instead of being hard-coded to those defaults;
 * {@link #build(String)} waits until the application answers a GraphQL request (TypeScript's
 * {@code ensureProviderIsReady} is an empty TODO); the counters and queries read the {@code .booster} files instead of
 * returning {@code -1} and {@code []}.
 */
public final class LocalTestHelper {

  /**
   * Where the application runs.
   *
   * @param baseDirectory the project directory holding {@code .booster} ({@code null}: the working directory)
   * @param httpPort the HTTP port
   * @param webSocketPort the websocket port
   * @param readinessTimeout how long {@link #build} waits for the application ({@link Duration#ZERO}: one attempt;
   *     {@code null}: no check)
   */
  public record Options(Path baseDirectory, String httpPort, String webSocketPort, Duration readinessTimeout) {

    /** The default readiness timeout. */
    public static final Duration DEFAULT_READINESS_TIMEOUT = Duration.ofSeconds(30);

    /**
     * @return the working directory, the ports of the environment, 30 s to become ready
     */
    public static Options defaults() {
      return new Options(null, InternalInfo.boosterLocalPort(),
          EnvironmentVariables.get(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT)
              .orElse(WebSocketServerAdapter.DEFAULT_PORT),
          DEFAULT_READINESS_TIMEOUT);
    }

    /**
     * @param directory the project directory
     * @return a copy using it
     */
    public Options withBaseDirectory(Path directory) {
      return new Options(directory, httpPort, webSocketPort, readinessTimeout);
    }

    /**
     * @param http the HTTP port
     * @param webSocket the websocket port
     * @return a copy using them
     */
    public Options withPorts(int http, int webSocket) {
      return new Options(baseDirectory, Integer.toString(http), Integer.toString(webSocket), readinessTimeout);
    }

    /**
     * @param timeout the readiness timeout ({@code null} disables the check)
     * @return a copy using it
     */
    public Options withReadinessTimeout(Duration timeout) {
      return new Options(baseDirectory, httpPort, webSocketPort, timeout);
    }
  }

  private final ApplicationOutputs outputs;
  private final LocalCounters counters;
  private final LocalQueries queries;

  private LocalTestHelper(ApplicationOutputs outputs, LocalCounters counters, LocalQueries queries) {
    this.outputs = outputs;
    this.counters = counters;
    this.queries = queries;
  }

  /**
   * {@code LocalTestHelper.build(appName)} with {@link Options#defaults()}.
   *
   * @param appName the application name
   * @return the helper
   * @throws IllegalStateException when the application does not answer within 30 s
   */
  public static LocalTestHelper build(String appName) {
    return build(appName, Options.defaults());
  }

  /**
   * @param appName the application name
   * @param options where the application runs
   * @return the helper
   * @throws IllegalStateException when the application does not answer in time
   */
  public static LocalTestHelper build(String appName, Options options) {
    Objects.requireNonNull(options, "options");
    ApplicationOutputs outputs = new ApplicationOutputs(graphqlURL(options), websocketURL(options),
        healthURL(options));
    ensureProviderIsReady(outputs.graphqlURL(), options.readinessTimeout());
    return new LocalTestHelper(outputs, new LocalCounters(appName + "-app", options.baseDirectory()),
        new LocalQueries(options.baseDirectory()));
  }

  /**
   * @return the application URLs
   */
  public ApplicationOutputs outputs() {
    return outputs;
  }

  /**
   * @return the counters
   */
  public LocalCounters counters() {
    return counters;
  }

  /**
   * @return the raw queries
   */
  public LocalQueries queries() {
    return queries;
  }

  /**
   * Posts {@code { query: "query { __typename }" }} (the health sensor's probe) until the application answers 200.
   */
  private static void ensureProviderIsReady(String graphqlURL, Duration timeout) {
    if (timeout == null) {
      return;
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    String probe = BoosterJson.toJson(Map.of("query", "query { __typename }"));
    RuntimeException lastError;
    do {
      try {
        HttpService.request(graphqlURL, HttpService.Method.POST, probe, HttpService.PostConfiguration.accepting(200));
        return;
      } catch (RuntimeException e) {
        lastError = e;
      }
      try {
        Thread.sleep(Duration.ofMillis(250));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    } while (System.nanoTime() < deadline);
    throw new IllegalStateException("The local application at " + graphqlURL + " is not ready: "
        + lastError.getMessage(), lastError);
  }

  private static String graphqlURL(Options options) {
    return "http://localhost:" + options.httpPort() + "/graphql";
  }

  private static String healthURL(Options options) {
    return "http://localhost:" + options.httpPort() + "/sensor/health/";
  }

  private static String websocketURL(Options options) {
    return "ws://localhost:" + options.webSocketPort() + WebSocketServerAdapter.PATH;
  }
}
