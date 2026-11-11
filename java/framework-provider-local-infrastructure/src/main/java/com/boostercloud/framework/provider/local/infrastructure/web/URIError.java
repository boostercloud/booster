package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * The JavaScript {@code URIError} that {@code decodeURIComponent} raises; Express rewrites its message to
 * {@code Failed to decode param '<value>'} and sets status 400 when a route parameter is malformed.
 */
public class URIError extends RuntimeException implements HttpStatusError {

  /**
   * @param message the message
   */
  public URIError(String message) {
    super(message);
  }

  @Override
  public int status() {
    return 400;
  }

  @Override
  public String type() {
    return null;
  }
}
