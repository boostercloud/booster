package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * An error raised by the HTTP layer itself (body parsing, parameter decoding), carrying the status and type that the
 * Node libraries of the TypeScript server attach to theirs ({@code err.status}, {@code err.type}).
 *
 * <p>The error classes are named after their JavaScript counterparts ({@link PayloadTooLargeError},
 * {@link UnsupportedMediaTypeError}, {@link BadRequestError} from {@code http-errors}, {@link SyntaxError} and
 * {@link URIError}) because the server's error handler builds the response from the class name, exactly like
 * TypeScript's {@code requestFailed}: the status comes from {@code httpStatusCodeFor} (500 for all of these, since
 * none is a Booster error) and the title from {@code toClassTitle} ({@code "Payload Too Large Error"}). {@link #status()}
 * is informational, as in TypeScript, where nothing reads {@code err.status}.
 */
public interface HttpStatusError {

  /**
   * @return the HTTP status the error stands for ({@code err.status})
   */
  int status();

  /**
   * @return the body-parser error type ({@code entity.too.large}, {@code charset.unsupported}, ...), or {@code null}
   */
  String type();
}
