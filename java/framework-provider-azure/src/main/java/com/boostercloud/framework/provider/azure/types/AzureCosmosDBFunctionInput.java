package com.boostercloud.framework.provider.azure.types;

import java.util.List;

/**
 * Port of {@code AzureCosmosDBFunctionInput}: {@code { documents, context }} of a Cosmos DB change feed trigger.
 *
 * @param documents the changed documents as raw maps (Cosmos system properties included)
 * @param context the invocation context
 */
public record AzureCosmosDBFunctionInput(List<Object> documents, AzureInvocationContext context)
    implements AzureFunctionRawRequest {}
