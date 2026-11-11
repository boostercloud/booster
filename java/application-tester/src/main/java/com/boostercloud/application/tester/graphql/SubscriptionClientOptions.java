package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.Logger;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Options of {@link SubscriptionClient}: the {@code ClientOptions} of {@code subscriptions-transport-ws} 0.11.0,
 * with the same defaults.
 *
 * @param connectionParams the {@code connection_init} payload, evaluated on every (re)connection (default
 *     {@code {}}); if it throws, a {@code connection_error} message with payload {@code {}} is sent instead
 * @param connectionCallback called with {@code null} on every {@code connection_ack} and with the payload of every
 *     {@code connection_error} (default none)
 * @param minTimeout first "maximum time to connect" in milliseconds (default 1000; it grows by 1.2 per attempt)
 * @param timeout keep-alive timeout and cap of the time to connect, in milliseconds (default 30000)
 * @param reconnect reconnect when the socket closes (default {@code false}; Booster's helper uses {@code true})
 * @param reconnectionAttempts maximum reconnection attempts (default {@link Long#MAX_VALUE}, JavaScript's
 *     {@code Infinity})
 * @param lazy connect on the first operation instead of on construction (default {@code false})
 * @param inactivityTimeout close the socket this many milliseconds after the last operation ends; {@code 0}
 *     disables it (default)
 * @param socketLogger receives {@code [GraphQL socket] ...} debug lines for every socket event (default
 *     {@link Logger#NOOP}; the TypeScript helper logs them through its {@code ws} subclass)
 * @param random the random source of the reconnection jitter (default {@link Math#random()})
 */
public record SubscriptionClientOptions(
    Supplier<Map<String, Object>> connectionParams,
    Consumer<Object> connectionCallback,
    long minTimeout,
    long timeout,
    boolean reconnect,
    long reconnectionAttempts,
    boolean lazy,
    long inactivityTimeout,
    Logger socketLogger,
    DoubleSupplier random) {

  /** {@code MIN_WS_TIMEOUT}. */
  public static final long MIN_WS_TIMEOUT = 1000;

  /** {@code WS_TIMEOUT}. */
  public static final long WS_TIMEOUT = 30000;

  /** Canonical constructor; {@code null} suppliers/loggers take the defaults. */
  public SubscriptionClientOptions {
    connectionParams = connectionParams == null ? Map::of : connectionParams;
    socketLogger = socketLogger == null ? Logger.NOOP : socketLogger;
    random = random == null ? Math::random : random;
  }

  /**
   * @return the defaults
   */
  public static SubscriptionClientOptions defaults() {
    return builder().build();
  }

  /**
   * @return a builder with the defaults
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder of {@link SubscriptionClientOptions}. */
  public static final class Builder {
    private Supplier<Map<String, Object>> connectionParams = Map::of;
    private Consumer<Object> connectionCallback;
    private long minTimeout = MIN_WS_TIMEOUT;
    private long timeout = WS_TIMEOUT;
    private boolean reconnect;
    private long reconnectionAttempts = Long.MAX_VALUE;
    private boolean lazy;
    private long inactivityTimeout;
    private Logger socketLogger = Logger.NOOP;
    private DoubleSupplier random = Math::random;

    private Builder() {}

    /**
     * @param value see {@link SubscriptionClientOptions#connectionParams()}
     * @return this builder
     */
    public Builder connectionParams(Supplier<Map<String, Object>> value) {
      this.connectionParams = Objects.requireNonNull(value, "connectionParams");
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#connectionCallback()}
     * @return this builder
     */
    public Builder connectionCallback(Consumer<Object> value) {
      this.connectionCallback = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#minTimeout()}
     * @return this builder
     */
    public Builder minTimeout(long value) {
      this.minTimeout = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#timeout()}
     * @return this builder
     */
    public Builder timeout(long value) {
      this.timeout = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#reconnect()}
     * @return this builder
     */
    public Builder reconnect(boolean value) {
      this.reconnect = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#reconnectionAttempts()}
     * @return this builder
     */
    public Builder reconnectionAttempts(long value) {
      this.reconnectionAttempts = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#lazy()}
     * @return this builder
     */
    public Builder lazy(boolean value) {
      this.lazy = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#inactivityTimeout()}
     * @return this builder
     */
    public Builder inactivityTimeout(long value) {
      this.inactivityTimeout = value;
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#socketLogger()}
     * @return this builder
     */
    public Builder socketLogger(Logger value) {
      this.socketLogger = Objects.requireNonNull(value, "socketLogger");
      return this;
    }

    /**
     * @param value see {@link SubscriptionClientOptions#random()}
     * @return this builder
     */
    public Builder random(DoubleSupplier value) {
      this.random = Objects.requireNonNull(value, "random");
      return this;
    }

    /**
     * @return the options
     */
    public SubscriptionClientOptions build() {
      return new SubscriptionClientOptions(
          connectionParams, connectionCallback, minTimeout, timeout, reconnect, reconnectionAttempts, lazy,
          inactivityTimeout, socketLogger, random);
    }
  }
}
