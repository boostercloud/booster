package com.boostercloud.framework.provider.azure.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The part of the Azure Functions {@code InvocationContext} (TypeScript {@code @azure/functions} v4) the adapters
 * read. The Java entry points build it from the worker's {@code ExecutionContext}.
 *
 * @param invocationId the invocation id (becomes the {@code requestID} of GraphQL and health envelopes)
 * @param functionName the function name
 * @param triggerMetadata trigger metadata (for Web PubSub it may hold {@code connectionContext}); never {@code null}
 * @param nativeContext the worker's {@code ExecutionContext}, or {@code null}; never serialized
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record AzureInvocationContext(
    String invocationId, String functionName, Map<String, Object> triggerMetadata, @JsonIgnore Object nativeContext) {

  /** Canonical constructor; a {@code null} trigger metadata map becomes empty. */
  public AzureInvocationContext {
    triggerMetadata = triggerMetadata == null ? new LinkedHashMap<>() : triggerMetadata;
  }

  /**
   * @param invocationId the invocation id
   * @return a context with only an invocation id (tests and synthetic invocations)
   */
  public static AzureInvocationContext of(String invocationId) {
    return new AzureInvocationContext(invocationId, null, null, null);
  }
}
