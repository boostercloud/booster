package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;

/**
 * The second argument of a query handler, port of {@code QueryInfo}. {@code responseHeaders} is the mutable map
 * shared with the GraphQL dispatcher (headers set by the handler are returned to the client).
 *
 * @param requestID id of the request
 * @param responseHeaders mutable response headers
 * @param currentUser the user, or {@code null}
 * @param context the request context, or {@code null}
 */
public record QueryInfo(
    UUID requestID, Map<String, String> responseHeaders, UserEnvelope currentUser, ContextEnvelope context) {}
