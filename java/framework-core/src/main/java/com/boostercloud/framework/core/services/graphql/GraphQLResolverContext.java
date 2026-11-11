package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.services.pubsub.ReadModelPubSub;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;

/**
 * Port of {@code GraphQLResolverContext} ({@code services/graphql/common.ts}): the context of every GraphQL resolver.
 * {@code responseHeaders} is shared <b>by reference</b> with the command {@code Register} and the {@code QueryInfo}, so
 * what a handler writes there is sent with the response.
 *
 * @param connectionID the websocket connection, or {@code null} for HTTP requests
 * @param responseHeaders the mutable response headers
 * @param operation the GraphQL operation being run
 * @param requestID the request id
 * @param user the authenticated user, or {@code null}
 * @param storeSubscriptions whether subscription operations are stored (client requests) or executed (notifications)
 * @param pubSub where subscription resolvers get their read models from
 * @param context the request context, or {@code null}
 */
public record GraphQLResolverContext(
    String connectionID,
    Map<String, String> responseHeaders,
    GraphQLOperation operation,
    UUID requestID,
    UserEnvelope user,
    boolean storeSubscriptions,
    ReadModelPubSub pubSub,
    ContextEnvelope context) {}
