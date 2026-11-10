package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Port of {@code WebhookResponseType} ({@code webhook-response-type.ts}): how the result of a handler is sent. The
 * values are the TypeScript enum values ({@code text/plain}, {@code application/json}, {@code file}).
 */
public enum WebhookResponseType {
  /** {@code text}: the body is sent as is with {@code Content-Type: text/plain}. */
  TEXT("text/plain"),
  /** {@code json}: the body is serialised with {@code JSON.stringify} and sent as {@code application/json}. */
  JSON("application/json"),
  /** {@code file}: the body is sent as an attachment named after {@code file.name}. */
  FILE("file");

  private final String value;

  WebhookResponseType(String value) {
    this.value = value;
  }

  /**
   * @return the TypeScript enum value
   */
  @JsonValue
  public String value() {
    return value;
  }

  /**
   * @param value a TypeScript enum value ({@code text/plain}, {@code application/json}, {@code file})
   * @return the response type, or {@code null} when the value is none of them
   */
  public static WebhookResponseType fromValue(Object value) {
    for (WebhookResponseType type : values()) {
      if (type == value || type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }
}
