package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A GraphQL request the provider could not parse. Port of {@code GraphQLRequestEnvelopeError} (TypeScript
 * {@code Pick<GraphQLRequestEnvelope, 'eventType' | 'connectionID' | 'requestID'> & { error }}).
 *
 * @param requestID id of the request
 * @param eventType the event type
 * @param connectionID the websocket connection id, optional
 * @param error what went wrong
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "eventType", "connectionID", "error"})
public record GraphQLRequestEnvelopeError(
    UUID requestID,
    EventType eventType,
    String connectionID,
    Throwable error) implements GraphQLRequestEnvelopeOrError {
}
