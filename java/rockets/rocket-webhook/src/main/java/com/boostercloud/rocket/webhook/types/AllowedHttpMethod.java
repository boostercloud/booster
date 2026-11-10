package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Port of {@code AllowedHttpMethod} ({@code webhook-params.ts}): the HTTP methods a webhook can accept. Every webhook
 * route answers {@code GET} and {@code POST} at the transport level; the dispatcher rejects the methods that are not
 * in {@link WebhookParamsEvent#allowedMethods()} ({@code [POST]} when absent).
 */
public enum AllowedHttpMethod {
  /** {@code GET}. */
  GET("GET"),
  /** {@code POST}. */
  POST("POST");

  private final String value;

  AllowedHttpMethod(String value) {
    this.value = value;
  }

  /**
   * @return the method as it appears in requests (case-sensitive)
   */
  @JsonValue
  public String value() {
    return value;
  }
}
