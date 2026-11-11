package com.boostercloud.framework.provider.azure.types;

/**
 * Port of {@code AzureHttpFunctionInput}: {@code { request, context }} of an HTTP-triggered function (GraphQL,
 * health, rockets).
 *
 * @param request the HTTP request
 * @param context the invocation context
 */
public record AzureHttpFunctionInput(AzureHttpRequest request, AzureInvocationContext context)
    implements AzureFunctionRawRequest {}
