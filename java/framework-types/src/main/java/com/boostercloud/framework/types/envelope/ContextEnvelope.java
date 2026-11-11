package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The context of a request. Port of {@code ContextEnvelope}. {@code rawContext} holds the provider-native request
 * object and is never serialized.
 *
 * @param request the decoded request headers and body
 * @param rawContext the provider-native request (not serialized)
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record ContextEnvelope(Request request, @JsonIgnore Object rawContext) {

  /**
   * The decoded request.
   *
   * @param headers the request headers (a raw map in practice)
   * @param body the request body (a raw value)
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Request(Object headers, Object body) {}

  /**
   * @param headers the request headers
   * @param body the request body
   * @param rawContext the provider-native request
   * @return the context
   */
  public static ContextEnvelope of(Object headers, Object body, Object rawContext) {
    return new ContextEnvelope(new Request(headers, body), rawContext);
  }
}
