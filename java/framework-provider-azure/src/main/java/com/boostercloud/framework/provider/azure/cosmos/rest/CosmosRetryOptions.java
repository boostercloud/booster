package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.helpers.JsNumbers;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.function.Function;

/**
 * Throttling retry options ({@code connectionPolicy.retryOptions} of {@code @azure/cosmos}). Values are doubles
 * because TypeScript passes {@code Number(envVar)} through without validation ({@code NaN} disables the retries,
 * as it does in JavaScript).
 *
 * @param maxRetryAttemptCount maximum number of retries of a throttled request (default 9)
 * @param fixedRetryIntervalInMilliseconds fixed wait between retries; 0 uses the server's {@code x-ms-retry-after-ms}
 *     (default 0)
 * @param maxWaitTimeInSeconds maximum cumulative wait (default 30)
 */
public record CosmosRetryOptions(
    double maxRetryAttemptCount, double fixedRetryIntervalInMilliseconds, double maxWaitTimeInSeconds) {

  /** SDK default: 9 retries. */
  public static final double DEFAULT_MAX_RETRY_ATTEMPT_COUNT = 9;
  /** SDK default: use the server's retry-after. */
  public static final double DEFAULT_FIXED_RETRY_INTERVAL_IN_MILLISECONDS = 0;
  /** SDK default: 30 seconds. */
  public static final double DEFAULT_MAX_WAIT_TIME_IN_SECONDS = 30;

  /**
   * @return the SDK defaults
   */
  public static CosmosRetryOptions defaults() {
    return new CosmosRetryOptions(
        DEFAULT_MAX_RETRY_ATTEMPT_COUNT, DEFAULT_FIXED_RETRY_INTERVAL_IN_MILLISECONDS, DEFAULT_MAX_WAIT_TIME_IN_SECONDS);
  }

  /**
   * Port of the {@code connectionPolicy.retryOptions} block of {@code index.ts}: every setting that is a non-empty
   * string overrides the corresponding default ({@code Number(value)}).
   *
   * @return the options from {@code COSMOSDB_MAX_RETRIES}, {@code COSMOSDB_RETRY_INTERVAL} and
   *     {@code COSMOSDB_MAX_WAIT_TIME}
   */
  public static CosmosRetryOptions fromEnvironment() {
    return fromSettings(name -> EnvironmentVariables.get(name).orElse(null));
  }

  /**
   * @param settings app setting lookup
   * @return the options
   */
  public static CosmosRetryOptions fromSettings(Function<String, String> settings) {
    return new CosmosRetryOptions(
        numberOr(settings.apply(EnvironmentVarNames.COSMOS_DB_MAX_RETRIES), DEFAULT_MAX_RETRY_ATTEMPT_COUNT),
        numberOr(settings.apply(EnvironmentVarNames.COSMOS_DB_RETRY_INTERVAL), DEFAULT_FIXED_RETRY_INTERVAL_IN_MILLISECONDS),
        numberOr(settings.apply(EnvironmentVarNames.COSMOS_DB_MAX_WAIT_TIME), DEFAULT_MAX_WAIT_TIME_IN_SECONDS));
  }

  private static double numberOr(String value, double fallback) {
    return value == null || value.isEmpty() ? fallback : JsNumbers.number(value);
  }
}
