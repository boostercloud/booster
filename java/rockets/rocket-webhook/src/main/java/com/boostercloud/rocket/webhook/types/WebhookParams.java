package com.boostercloud.rocket.webhook.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of {@code webhook-params.ts}: the rocket function id and the route resolution of a webhook. TypeScript's
 * {@code WebhookParams} type ({@code Array<WebhookParamsEvent>}) is a {@code List<WebhookParamsEvent>} in Java; it is
 * the {@code parameters} of the rocket descriptors built by {@code BoosterWebhook}.
 */
public final class WebhookParams {

  /**
   * {@code functionID}: the id the rocket function is registered under ({@code config.registerRocketFunction}) and
   * that requests carry in {@code BOOSTER_ROCKET_FUNCTION_ID} (local request property, Azure app setting).
   */
  public static final String FUNCTION_ID = "rocket-webhook";

  /** What TypeScript renders for {@code `${param}`} in error messages. */
  static final String OBJECT_STRING = "[object Object]";

  private WebhookParams() {}

  /**
   * {@code getRoute(param)}: the {@code route} of a webhook, or its deprecated {@code origin} when {@code route} is
   * {@code null} ({@code param.route ?? param.origin}). An empty route does not fall back to the origin, and the
   * route is not normalised (no trimming, no leading-slash stripping).
   *
   * @param param the webhook
   * @return the route ({@code test}, {@code clients/other})
   * @throws IllegalStateException {@code Undefined route parameter [object Object]} when the result is {@code null}
   *     or empty
   */
  @SuppressWarnings("deprecation")
  public static String getRoute(WebhookParamsEvent param) {
    String endpoint = param.route() != null ? param.route() : param.origin();
    if (endpoint == null || endpoint.isEmpty()) {
      throw new IllegalStateException("Undefined route parameter " + OBJECT_STRING);
    }
    return endpoint;
  }

  /**
   * Reads the {@code parameters} of a rocket descriptor: the {@code List<WebhookParamsEvent>} given to
   * {@code BoosterWebhook} (TypeScript passes the same array by reference).
   *
   * @param parameters the descriptor parameters
   * @return the webhooks, in order (an unmodifiable copy)
   * @throws IllegalArgumentException when the parameters are not a list of {@link WebhookParamsEvent}
   */
  public static List<WebhookParamsEvent> from(Object parameters) {
    if (!(parameters instanceof List<?> list)) {
      throw new IllegalArgumentException("The parameters of the webhook rocket must be a List<WebhookParamsEvent>, got "
          + (parameters == null ? "undefined" : parameters.getClass().getName()));
    }
    List<WebhookParamsEvent> params = new ArrayList<>(list.size());
    for (Object element : list) {
      if (!(element instanceof WebhookParamsEvent param)) {
        throw new IllegalArgumentException("The parameters of the webhook rocket must be a List<WebhookParamsEvent>, "
            + "got an element of type " + (element == null ? "undefined" : element.getClass().getName()));
      }
      params.add(param);
    }
    return Collections.unmodifiableList(params);
  }
}
