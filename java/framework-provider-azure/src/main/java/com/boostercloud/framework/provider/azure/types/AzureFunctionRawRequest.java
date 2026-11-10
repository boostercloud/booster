package com.boostercloud.framework.provider.azure.types;

/**
 * Port of the {@code AzureFunctionRawRequest} union: the wrapper object every Azure function hands to the Booster
 * entry point ({@code { request, context }}, {@code { documents, context }}, ...). Implemented by
 * {@link AzureHttpFunctionInput}, {@link AzureCosmosDBFunctionInput}, {@link AzureEventHubFunctionInput},
 * {@link AzureTimerFunctionInput}, {@link AzureWebPubSubFunctionInput} and the specialised wrappers of the library
 * ({@code SubscriptionNotifierInput}, {@code ScheduledCommandInput}, {@code EventHubConsumerInput}).
 *
 * <p>The type guards of {@link AzureFunctionTypes} are structural, like TypeScript's: they also accept raw maps with
 * the same keys, so code (and tests) may pass {@code Map.of("documents", ..., "context", ...)}.
 */
public interface AzureFunctionRawRequest {

  /**
   * @return the invocation context
   */
  AzureInvocationContext context();
}
