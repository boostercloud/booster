package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * {@code http-errors}' 400 error, raised when a body cannot be read completely ({@code request aborted}, type
 * {@code request.aborted}) or cannot be inflated ({@code invalid gzip data}...).
 */
public class BadRequestError extends RuntimeException implements HttpStatusError {

  private final String type;

  /**
   * @param message the message
   * @param type the body-parser error type
   * @param cause the underlying failure, or {@code null}
   */
  public BadRequestError(String message, String type, Throwable cause) {
    super(message, cause);
    this.type = type;
  }

  @Override
  public int status() {
    return 400;
  }

  @Override
  public String type() {
    return type;
  }
}
