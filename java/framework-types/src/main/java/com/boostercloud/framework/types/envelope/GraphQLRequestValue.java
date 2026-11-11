package com.boostercloud.framework.types.envelope;

/**
 * Marker of the possible {@link GraphQLRequestEnvelope#value()} types: {@link GraphQLOperation} (HTTP) and
 * {@code com.boostercloud.framework.types.graphql.GraphQLClientMessage} (websocket).
 */
public interface GraphQLRequestValue {}
