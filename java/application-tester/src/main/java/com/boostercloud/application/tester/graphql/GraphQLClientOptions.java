package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import java.time.Duration;
import java.util.Objects;

/**
 * Settings of the GraphQL test clients. The defaults reproduce what the TypeScript {@code GraphQLHelper} builds with
 * Apollo Client.
 *
 * @param addTypename add {@code __typename} to every selection set like Apollo's {@code InMemoryCache} (default
 *     {@code true}, so responses carry {@code __typename} keys exactly as in the TypeScript suites)
 * @param includeUnusedVariables send variables the document does not reference over HTTP (Apollo's
 *     {@code HttpLink} option, default {@code false}: they are stripped; the websocket always sends them all)
 * @param requestTimeout timeout of each HTTP request (and of a query/mutation routed through the websocket);
 *     {@code null} = none, like {@code cross-fetch} (default)
 * @param connectionTimeout how long {@code clientWithSubscriptions} and {@code reconnect()} wait for the server's
 *     {@code connection_ack} (Java only: the TypeScript promise waits forever; default 10 minutes)
 * @param socketLogger receives the {@code [GraphQL socket] ...} debug lines of every websocket event (default
 *     {@link ConsoleLogger#INSTANCE}, as {@code console.debug}; use {@link Logger#NOOP} to silence them)
 */
public record GraphQLClientOptions(
    boolean addTypename,
    boolean includeUnusedVariables,
    Duration requestTimeout,
    Duration connectionTimeout,
    Logger socketLogger) {

  /** Default {@link #connectionTimeout()}: 10 minutes. */
  public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofMinutes(10);

  /** Canonical constructor; a {@code null} connection timeout or logger takes the default. */
  public GraphQLClientOptions {
    connectionTimeout = connectionTimeout == null ? DEFAULT_CONNECTION_TIMEOUT : connectionTimeout;
    socketLogger = socketLogger == null ? ConsoleLogger.INSTANCE : socketLogger;
  }

  /**
   * @return the defaults
   */
  public static GraphQLClientOptions defaults() {
    return builder().build();
  }

  /**
   * @return a builder with the defaults
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return a builder initialised with these options
   */
  public Builder toBuilder() {
    return new Builder()
        .addTypename(addTypename)
        .includeUnusedVariables(includeUnusedVariables)
        .requestTimeout(requestTimeout)
        .connectionTimeout(connectionTimeout)
        .socketLogger(socketLogger);
  }

  /** Builder of {@link GraphQLClientOptions}. */
  public static final class Builder {
    private boolean addTypename = true;
    private boolean includeUnusedVariables;
    private Duration requestTimeout;
    private Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private Logger socketLogger = ConsoleLogger.INSTANCE;

    private Builder() {}

    /**
     * @param value see {@link GraphQLClientOptions#addTypename()}
     * @return this builder
     */
    public Builder addTypename(boolean value) {
      this.addTypename = value;
      return this;
    }

    /**
     * @param value see {@link GraphQLClientOptions#includeUnusedVariables()}
     * @return this builder
     */
    public Builder includeUnusedVariables(boolean value) {
      this.includeUnusedVariables = value;
      return this;
    }

    /**
     * @param value see {@link GraphQLClientOptions#requestTimeout()}
     * @return this builder
     */
    public Builder requestTimeout(Duration value) {
      this.requestTimeout = value;
      return this;
    }

    /**
     * @param value see {@link GraphQLClientOptions#connectionTimeout()}
     * @return this builder
     */
    public Builder connectionTimeout(Duration value) {
      this.connectionTimeout = Objects.requireNonNull(value, "connectionTimeout");
      return this;
    }

    /**
     * @param value see {@link GraphQLClientOptions#socketLogger()}
     * @return this builder
     */
    public Builder socketLogger(Logger value) {
      this.socketLogger = Objects.requireNonNull(value, "socketLogger");
      return this;
    }

    /**
     * @return the options
     */
    public GraphQLClientOptions build() {
      return new GraphQLClientOptions(
          addTypename, includeUnusedVariables, requestTimeout, connectionTimeout, socketLogger);
    }
  }
}
