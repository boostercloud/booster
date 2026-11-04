package io.boostercloud.booster.core;

import java.util.Map;
import java.util.UUID;

public record RequestContext(String requestId, UserContext user, Map<String, String> metadata,
                             @com.fasterxml.jackson.annotation.JsonIgnore RequestData request,
                             @com.fasterxml.jackson.annotation.JsonIgnore ResponseHeaders response) {
  public RequestContext(String requestId, UserContext user, Map<String,String> metadata) {
    this(requestId, user, metadata, RequestData.empty(), new ResponseHeaders());
  }
  public RequestContext(String requestId, UserContext user, Map<String,String> metadata, RequestData request) {
    this(requestId, user, metadata, request, new ResponseHeaders());
  }
  public RequestContext {
    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
    user = user == null ? UserContext.anonymous() : user;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    request = request == null ? RequestData.empty() : request;
    response = response == null ? new ResponseHeaders() : response;
  }
  public RequestContext withRequestId(String id) { return new RequestContext(id, user, metadata, request, response); }
  public static RequestContext anonymous() { return new RequestContext(null, null, Map.of()); }
}
