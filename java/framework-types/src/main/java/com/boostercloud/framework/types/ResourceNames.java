package com.boostercloud.framework.types;

/**
 * Cloud resource names derived from {@link BoosterConfig#appName}. Port of the TypeScript {@code ResourceNames}
 * object returned by {@code config.resourceNames}; no sanitisation is applied.
 *
 * @param applicationStack {@code <appName>-app}
 * @param eventsStore {@code <appName>-app-events-store}
 * @param dispatchedEventsStore {@code <appName>-app-dispatched-events}
 * @param eventsDedup {@code <appName>-app-events-dedup}
 * @param subscriptionsStore {@code <appName>-app-subscriptions-store}
 * @param connectionsStore {@code <appName>-app-connections-store}
 * @param streamTopic the event stream topic ({@code eventStreamConfiguration.parameters.streamTopic}, default
 *     {@code booster_events})
 */
public record ResourceNames(
    String applicationStack,
    String eventsStore,
    String dispatchedEventsStore,
    String eventsDedup,
    String subscriptionsStore,
    String connectionsStore,
    String streamTopic) {

  /** Default stream topic. */
  public static final String DEFAULT_STREAM_TOPIC = "booster_events";

  /**
   * @param appName a non-empty application name
   * @param streamTopic the stream topic
   * @return the names
   */
  static ResourceNames of(String appName, String streamTopic) {
    String stack = appName + "-app";
    return new ResourceNames(
        stack,
        stack + "-events-store",
        stack + "-dispatched-events",
        stack + "-events-dedup",
        stack + "-subscriptions-store",
        stack + "-connections-store",
        streamTopic);
  }

  /**
   * @param readModelName a read model class name
   * @return {@code <appName>-app-<readModelName>}
   */
  public String forReadModel(String readModelName) {
    return applicationStack + "-" + readModelName;
  }
}
