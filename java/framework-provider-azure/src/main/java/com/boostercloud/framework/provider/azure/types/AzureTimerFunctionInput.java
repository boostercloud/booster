package com.boostercloud.framework.provider.azure.types;

/**
 * Port of {@code AzureTimerFunctionInput}: {@code { timer, context }} of a timer trigger.
 *
 * @param timer the timer information
 * @param context the invocation context
 */
public record AzureTimerFunctionInput(TimerInfo timer, AzureInvocationContext context)
    implements AzureFunctionRawRequest {}
