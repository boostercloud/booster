package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * What {@code ProviderGraphQLLibrary.rawToEnvelope} returns: a parsed request or an error (TypeScript discriminates
 * with {@code 'error' in envelope}; Java with a type pattern).
 */
public sealed interface GraphQLRequestEnvelopeOrError permits GraphQLRequestEnvelope, GraphQLRequestEnvelopeError {
  /**
   * @return id of the request
   */
  UUID requestID();

  /**
   * @return the event type
   */
  EventType eventType();

  /**
   * @return the websocket connection id, or {@code null}
   */
  String connectionID();
}
