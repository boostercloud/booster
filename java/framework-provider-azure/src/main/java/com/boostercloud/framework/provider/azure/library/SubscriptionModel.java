package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.has;

import com.boostercloud.framework.provider.azure.types.AzureFunctionRawRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code library/subscription-model.ts}. {@code RawEvent} (a read model document from the change feed, with
 * the Cosmos system properties) is a raw {@code Map<String, Object>} in Java.
 */
public final class SubscriptionModel {

  private SubscriptionModel() {}

  /**
   * Port of {@code SubscriptionNotifierInput}: {@code { documents, context, typeName }} of a read model's
   * {@code <ReadModel>-subscriptions-notifier} function.
   *
   * @param documents the changed read model documents
   * @param context the invocation context
   * @param typeName the read model name
   */
  public record SubscriptionNotifierInput(List<Object> documents, AzureInvocationContext context, String typeName)
      implements AzureFunctionRawRequest {}

  /**
   * {@code isSubscriptionNotifierInput}: has {@code documents}, {@code context} and {@code typeName}, and
   * {@code documents} is an array.
   *
   * @param input any value
   * @return whether it is a subscriptions-notifier input
   */
  public static boolean isSubscriptionNotifierInput(Object input) {
    return (input instanceof Map<?, ?> || input instanceof AzureFunctionRawRequest)
        && has(input, "documents") && has(input, "context") && has(input, "typeName")
        && get(input, "documents") instanceof List<?>;
  }
}
