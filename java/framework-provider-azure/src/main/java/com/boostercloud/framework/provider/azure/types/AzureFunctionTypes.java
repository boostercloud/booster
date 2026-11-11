package com.boostercloud.framework.provider.azure.types;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.has;

import com.boostercloud.framework.types.BoosterJson;
import java.util.List;

/**
 * Port of the type guards of {@code types/azure-func-types.ts}. They are structural, like the TypeScript duck typing:
 * an input is recognised by the properties it has, whether it is one of the records of this package or a raw map with
 * the same keys ({@code 'key' in input} is true even when the value is {@code null}).
 */
public final class AzureFunctionTypes {

  private AzureFunctionTypes() {}

  /**
   * {@code isHttpFunctionInput}: has {@code request} and {@code context}, and {@code request.method} is a string.
   *
   * @param input any value
   * @return whether it is an HTTP function input
   */
  public static boolean isHttpFunctionInput(Object input) {
    return isObject(input) && has(input, "request") && has(input, "context")
        && get(get(input, "request"), "method") instanceof String;
  }

  /**
   * {@code isCosmosDBFunctionInput}: has {@code documents} and {@code context}, and {@code documents} is an array.
   *
   * @param input any value
   * @return whether it is a Cosmos DB change feed input
   */
  public static boolean isCosmosDBFunctionInput(Object input) {
    return isObject(input) && has(input, "documents") && has(input, "context")
        && get(input, "documents") instanceof List<?>;
  }

  /**
   * {@code isEventHubFunctionInput}: has {@code messages} and {@code context}, and {@code messages} is an array.
   *
   * @param input any value
   * @return whether it is an Event Hub input
   */
  public static boolean isEventHubFunctionInput(Object input) {
    return isObject(input) && has(input, "messages") && has(input, "context")
        && get(input, "messages") instanceof List<?>;
  }

  /**
   * {@code isTimerFunctionInput}: has {@code timer} and {@code context}, and {@code timer.isPastDue} is a boolean.
   *
   * @param input any value
   * @return whether it is a timer input
   */
  public static boolean isTimerFunctionInput(Object input) {
    return isObject(input) && has(input, "timer") && has(input, "context")
        && get(get(input, "timer"), "isPastDue") instanceof Boolean;
  }

  /**
   * {@code isWebPubSubFunctionInput}: has {@code request} and {@code context}, and a string {@code connectionId} in
   * either {@code request.connectionContext} or {@code context.triggerMetadata.connectionContext}.
   *
   * @param input any value
   * @return whether it is a Web PubSub input
   */
  public static boolean isWebPubSubFunctionInput(Object input) {
    if (!isObject(input) || !has(input, "request") || !has(input, "context")) {
      return false;
    }
    if (get(get(get(input, "request"), "connectionContext"), "connectionId") instanceof String) {
      return true;
    }
    return get(get(get(get(input, "context"), "triggerMetadata"), "connectionContext"), "connectionId")
        instanceof String;
  }

  /**
   * {@code getWebPubSubConnectionContext}: {@code request.connectionContext ?? context.triggerMetadata.connectionContext}
   * (the request location wins whenever it is present, even without a {@code connectionId}).
   *
   * @param input a Web PubSub input (record or raw map)
   * @return the connection context, or {@code null}
   */
  public static WebPubSubConnectionContext getWebPubSubConnectionContext(Object input) {
    Object context = get(get(input, "request"), "connectionContext");
    if (context == null) {
      context = get(get(get(input, "context"), "triggerMetadata"), "connectionContext");
    }
    if (context == null) {
      return null;
    }
    if (context instanceof WebPubSubConnectionContext typed) {
      return typed;
    }
    return BoosterJson.convert(context, WebPubSubConnectionContext.class);
  }

  /**
   * @param input any value
   * @return {@code typeof input === 'object' && input !== null} restricted to the shapes the guards understand
   */
  private static boolean isObject(Object input) {
    return input != null && !(input instanceof List<?>) && !(input instanceof CharSequence)
        && !(input instanceof Number) && !(input instanceof Boolean);
  }
}
