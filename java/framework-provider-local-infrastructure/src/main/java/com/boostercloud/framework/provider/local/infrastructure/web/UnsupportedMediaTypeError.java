package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * {@code http-errors}' 415 error: a body with an unsupported charset ({@code unsupported charset "LATIN1"}, type
 * {@code charset.unsupported}) or content encoding ({@code unsupported content encoding "br"}, type
 * {@code encoding.unsupported}).
 */
public class UnsupportedMediaTypeError extends RuntimeException implements HttpStatusError {

  private final String type;

  /**
   * @param message the message
   * @param type the body-parser error type
   */
  public UnsupportedMediaTypeError(String message, String type) {
    super(message);
    this.type = type;
  }

  @Override
  public int status() {
    return 415;
  }

  @Override
  public String type() {
    return type;
  }
}
