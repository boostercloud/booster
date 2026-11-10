package com.boostercloud.application.tester.graphql;

/**
 * A successful HTTP response whose body is not JSON: Apollo's {@code ServerParseError}. The message is the JSON
 * parser's (Jackson's, where TypeScript shows V8's {@code JSON.parse} message); the cause is the parser exception.
 */
public final class ServerParseError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The HTTP status code. */
  private final int statusCode;

  /** The body text. */
  private final String bodyText;

  /**
   * @param cause the parser failure
   * @param statusCode the HTTP status code
   * @param bodyText the body text
   */
  public ServerParseError(Throwable cause, int statusCode, String bodyText) {
    super(cause.getMessage(), cause);
    this.statusCode = statusCode;
    this.bodyText = bodyText;
  }

  /**
   * @return the HTTP status code
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * @return the body text
   */
  public String bodyText() {
    return bodyText;
  }
}
