package com.boostercloud.framework.provider.azure.eventhubs;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.helpers.JsNumbers;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.Locale;
import java.util.function.Function;

/**
 * Retry options of the producer ({@code retryOptions} of {@code @azure/event-hubs}).
 *
 * @param maxRetries retries after the first attempt
 * @param mode fixed or exponential delays
 * @param retryDelayInMs base delay (SDK default 30 000 ms)
 * @param maxRetryDelayInMs maximum delay (SDK default 90 000 ms)
 */
public record EventHubRetryOptions(int maxRetries, RetryMode mode, long retryDelayInMs, long maxRetryDelayInMs) {

  /** {@code DEFAULT_MAX_RETRY} of {@code index.ts}. */
  public static final int DEFAULT_MAX_RETRY = 5;
  /** {@code DEFAULT_EVENT_HUB_MODE} of {@code index.ts}. */
  public static final RetryMode DEFAULT_EVENT_HUB_MODE = RetryMode.EXPONENTIAL;
  /** {@code @azure/core-amqp} default base delay. */
  public static final long DEFAULT_RETRY_DELAY_IN_MS = 30_000;
  /** {@code @azure/core-amqp} default maximum delay. */
  public static final long DEFAULT_MAX_RETRY_DELAY_IN_MS = 90_000;

  /**
   * @param maxRetries retries
   * @param mode the mode
   * @return options with the SDK default delays
   */
  public static EventHubRetryOptions of(int maxRetries, RetryMode mode) {
    return new EventHubRetryOptions(maxRetries, mode, DEFAULT_RETRY_DELAY_IN_MS, DEFAULT_MAX_RETRY_DELAY_IN_MS);
  }

  /**
   * Port of {@code index.ts}: {@code maxRetries = EVENTHUB_MAX_RETRIES ? Number(EVENTHUB_MAX_RETRIES) : 5};
   * {@code mode} is {@code FIXED} only when {@code EVENTHUB_MAX_RETRIES} is set <b>and</b> {@code EVENTHUB_MODE}
   * upper-cased is {@code FIXED} (quirk Q5), exponential otherwise.
   *
   * @return the options from the app settings
   */
  public static EventHubRetryOptions fromEnvironment() {
    return fromSettings(name -> EnvironmentVariables.get(name).orElse(null));
  }

  /**
   * @param settings app setting lookup
   * @return the options
   */
  public static EventHubRetryOptions fromSettings(Function<String, String> settings) {
    String maxRetriesSetting = settings.apply(EnvironmentVarNames.EVENT_HUB_MAX_RETRIES);
    String modeSetting = settings.apply(EnvironmentVarNames.EVENT_HUB_MODE);
    boolean maxRetriesSet = maxRetriesSetting != null && !maxRetriesSetting.isEmpty();
    double parsed = maxRetriesSet ? JsNumbers.number(maxRetriesSetting) : DEFAULT_MAX_RETRY;
    int maxRetries = Double.isNaN(parsed) ? 0 : (int) parsed;
    RetryMode mode = maxRetriesSet && modeSetting != null && modeSetting.toUpperCase(Locale.ROOT).equals("FIXED")
        ? RetryMode.FIXED
        : DEFAULT_EVENT_HUB_MODE;
    return of(maxRetries, mode);
  }
}
