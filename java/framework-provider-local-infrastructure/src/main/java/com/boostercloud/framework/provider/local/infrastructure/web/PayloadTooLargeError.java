package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * {@code http-errors}' 413 error: a parsed body larger than the parser limit ({@code request entity too large}, type
 * {@code entity.too.large}) or a form with too many parameters ({@code too many parameters}, type
 * {@code parameters.too.many}).
 */
public class PayloadTooLargeError extends RuntimeException implements HttpStatusError {

  private final String type;

  /**
   * @param message the message
   * @param type the body-parser error type
   */
  public PayloadTooLargeError(String message, String type) {
    super(message);
    this.type = type;
  }

  @Override
  public int status() {
    return 413;
  }

  @Override
  public String type() {
    return type;
  }
}
