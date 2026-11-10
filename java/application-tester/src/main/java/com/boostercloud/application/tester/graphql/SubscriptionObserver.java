package com.boostercloud.application.tester.graphql;

/**
 * Receives the results of a {@link SubscriptionObservable} (an Apollo/zen-observable {@code Observer}). Methods are
 * called on the client's event loop, one at a time; they must not block waiting for the same client.
 */
public interface SubscriptionObserver {

  /**
   * A result without errors.
   *
   * @param result the result
   */
  default void next(GraphQLResponse result) {}

  /**
   * The subscription failed; no more calls follow. A result with GraphQL errors arrives here as a
   * {@link GraphQLClientException} (Apollo's {@code ApolloError}), an {@code error} message of the server as a
   * {@link GraphQLClientException} whose message is the first error's message, and a transport failure of an
   * HTTP-routed subscription as the raw {@link ServerError}/{@link ServerParseError}/{@link FetchError}.
   *
   * <p>The default implementation reports the error to stderr, like zen-observable rethrowing an unhandled error.
   *
   * @param error the failure
   */
  default void error(Throwable error) {
    EventLoop.reportUncaught(error);
  }

  /** The subscription ended normally; no more calls follow. */
  default void complete() {}
}
