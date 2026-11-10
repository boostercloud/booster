package com.boostercloud.framework.provider.azure.types;

/**
 * Port of {@code AzureWebPubSubFunctionInput}: {@code { request, context }} of a Web PubSub trigger. The connection
 * context may be in {@code request.connectionContext} or in {@code context.triggerMetadata.connectionContext}
 * (see {@link AzureFunctionTypes#getWebPubSubConnectionContext(Object)}).
 *
 * @param request the Web PubSub request
 * @param context the invocation context
 */
public record AzureWebPubSubFunctionInput(AzureWebPubSubRequest request, AzureInvocationContext context)
    implements AzureFunctionRawRequest {}
