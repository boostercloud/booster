package com.boostercloud.framework.common;

/**
 * A failed {@link HttpService#request}. TypeScript rejects with plain {@code Error}s told apart by their message
 * ({@code HTTP status code 503}, {@code Request time out}, {@code Unknown HTTP status code}); the messages are kept.
 */
public class HttpServiceError extends RuntimeException {
  /**
   * @param message the error message
   * @param cause the underlying failure, or {@code null}
   */
  public HttpServiceError(String message, Throwable cause) {
    super(message, cause);
  }
}
