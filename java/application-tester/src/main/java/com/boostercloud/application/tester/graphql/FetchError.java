package com.boostercloud.application.tester.graphql;

/**
 * The HTTP request could not be performed (connection refused, reset, timeout): the {@code FetchError} of
 * {@code node-fetch} that {@code cross-fetch} rejects with. Message:
 * {@code request to <url> failed, reason: <reason>}; the cause is the I/O exception.
 */
public final class FetchError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param url the requested URL
   * @param cause the I/O failure
   */
  public FetchError(String url, Throwable cause) {
    super("request to " + url + " failed, reason: " + reason(cause), cause);
  }

  /** The first non-empty message of the cause chain, or the cause's class name. */
  static String reason(Throwable cause) {
    for (Throwable current = cause; current != null; current = current.getCause()) {
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        return current.getMessage();
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return cause == null ? "unknown" : cause.getClass().getSimpleName();
  }
}
