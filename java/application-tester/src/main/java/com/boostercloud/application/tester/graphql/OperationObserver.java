package com.boostercloud.application.tester.graphql;

import java.util.Map;

/**
 * Receives the messages of one operation of a {@link SubscriptionClient}: the raw observer of
 * {@code SubscriptionClient.request(...).subscribe(...)}. Called on the client's event loop.
 */
public interface OperationObserver {

  /**
   * A {@code data} message.
   *
   * @param result the payload (an execution result {@code {data, errors?}}, errors formatted as a list)
   */
  void next(Map<String, Object> result);

  /**
   * An {@code error} message, or an operation that could not be started.
   *
   * @param error the error; for server errors a {@link GraphQLClientException} whose message is the first error's
   */
  void error(Throwable error);

  /** A {@code complete} message. */
  void complete();
}
