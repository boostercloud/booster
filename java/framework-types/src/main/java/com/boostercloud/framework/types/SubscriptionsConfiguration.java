package com.boostercloud.framework.types;

/**
 * GraphQL subscription limits ({@code config.subscriptions}), mutable like the TypeScript object.
 */
public class SubscriptionsConfiguration {
  /** Lifetime of a websocket connection record, in seconds (default 7 days). */
  public long maxConnectionDurationInSeconds = 7L * 24 * 60 * 60;

  /** Lifetime of a subscription record, in seconds (default 2 days). */
  public long maxDurationInSeconds = 2L * 24 * 60 * 60;
}
