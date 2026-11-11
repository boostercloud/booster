package com.boostercloud.application.tester.graphql;

/**
 * The server answered, but not with a usable GraphQL result: Apollo's {@code ServerError}. Thrown (as the
 * {@link GraphQLClientException#networkError()}) with the messages of Apollo's {@code parseAndCheckHttpResponse}:
 * {@code Response not successful: Received status code <status>} for statuses {@code >= 300}, and
 * {@code Server response was missing for query '<operationName>'.} for a body without {@code data} and
 * {@code errors}.
 */
public final class ServerError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The HTTP status code. */
  private final int statusCode;

  /** The parsed body (raw JSON value), or the body text when it is not JSON. */
  private final transient Object result;

  /** The body text. */
  private final String bodyText;

  /**
   * @param message the error message
   * @param statusCode the HTTP status code
   * @param result the parsed body, or the body text when it is not JSON
   * @param bodyText the body text
   */
  public ServerError(String message, int statusCode, Object result, String bodyText) {
    super(message);
    this.statusCode = statusCode;
    this.result = result;
    this.bodyText = bodyText;
  }

  /**
   * @return the HTTP status code
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * @return the parsed body (raw JSON value), or the body text when it is not JSON
   */
  public Object result() {
    return result;
  }

  /**
   * @return the body text
   */
  public String bodyText() {
    return bodyText;
  }
}
