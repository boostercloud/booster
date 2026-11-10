package com.boostercloud.framework.provider.azure.types;

import java.util.List;

/**
 * Port of {@code AzureEventHubFunctionInput}: {@code { messages, context }} of an Event Hub trigger with cardinality
 * {@code many}.
 *
 * @param messages the message bodies (JSON strings, or already parsed raw values)
 * @param context the invocation context
 */
public record AzureEventHubFunctionInput(List<Object> messages, AzureInvocationContext context)
    implements AzureFunctionRawRequest {}
