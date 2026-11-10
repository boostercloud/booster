package com.boostercloud.framework.provider.azure.webpubsub;

/** A failed Web PubSub request ({@code RestError} of {@code @azure/core-rest-pipeline}). */
public class WebPubSubException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;

  /**
   * @param message what went wrong
   * @param statusCode the HTTP status, or 0 for connection failures
   * @param cause the cause, or {@code null}
   */
  public WebPubSubException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * @return the HTTP status ({@code error.statusCode}), or 0
   */
  public int statusCode() {
    return statusCode;
  }
}
