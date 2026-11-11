package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * Static helpers of {@code errors.ts}.
 */
public final class BoosterErrors {

  private static final Map<String, Integer> HTTP_STATUS_BY_CLASS_NAME = Map.of(
      "InvalidParameterError", 400,
      "InvalidProtocolError", 400,
      "NotAuthorizedError", 401,
      "BoosterTokenExpiredError", 401,
      "BoosterTokenNotBeforeError", 401,
      "NotFoundError", 404,
      "InvalidVersionError", 422);

  private BoosterErrors() {}

  /**
   * {@code httpStatusCodeFor(error)}: the HTTP status of an error, looked up by the <b>exact simple class name</b>
   * (like TypeScript's {@code error.constructor.name}): a subclass of {@code NotFoundError} gets 500, and an
   * unrelated class named {@code NotFoundError} gets 404.
   *
   * @param error any throwable
   * @return 400, 401, 404, 422 or 500
   */
  public static int httpStatusCodeFor(Throwable error) {
    if (error == null) {
      return 500;
    }
    return HTTP_STATUS_BY_CLASS_NAME.getOrDefault(error.getClass().getSimpleName(), 500);
  }

  /**
   * The name of an error, used by {@code retryWithBackoff}'s {@code nonRetryableErrors}/{@code retryableErrors}: the
   * simple class name. (TypeScript matches {@code error.name}, which Booster errors never set, so they were all named
   * {@code "Error"}; Java uses the class name for every error, see the README.)
   *
   * @param error any throwable
   * @return its simple class name
   */
  public static String errorName(Throwable error) {
    return error.getClass().getSimpleName();
  }

  /**
   * The string JavaScript prints for an error ({@code Error.prototype.toString}): {@code "<name>: <message>"}, just
   * the name when the message is empty, just the message when the name is empty.
   *
   * @param error any throwable
   * @return its JavaScript-like text
   */
  public static String errorToString(Throwable error) {
    if (error == null) {
      return "undefined";
    }
    String name = errorName(error);
    String message = error.getMessage() == null ? "" : error.getMessage();
    if (name.isEmpty()) {
      return message;
    }
    return message.isEmpty() ? name : name + ": " + message;
  }
}
