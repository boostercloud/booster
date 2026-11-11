package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * The JavaScript {@code SyntaxError} body-parser raises for a malformed JSON body (status 400, type
 * {@code entity.parse.failed}); the error handler answers it with
 * {@code 500 {"title":"Syntax Error","reason":...}}, as TypeScript does.
 */
public class SyntaxError extends RuntimeException implements HttpStatusError {

  /**
   * @param message the message
   * @param cause the parser failure, or {@code null}
   */
  public SyntaxError(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public int status() {
    return 400;
  }

  @Override
  public String type() {
    return "entity.parse.failed";
  }
}
