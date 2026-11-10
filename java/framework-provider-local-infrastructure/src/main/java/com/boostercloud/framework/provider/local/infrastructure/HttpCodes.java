package com.boostercloud.framework.provider.local.infrastructure;

/**
 * The HTTP status codes of the local server. Port of the {@code HttpCodes} enum of {@code http.ts}.
 */
public enum HttpCodes {
  /** {@code Ok = 200}. */
  OK(200),
  /** {@code BadRequest = 400}. */
  BAD_REQUEST(400),
  /** {@code NotAuthorized = 403}. */
  NOT_AUTHORIZED(403),
  /** {@code InternalError = 500}. */
  INTERNAL_ERROR(500);

  private final int code;

  HttpCodes(int code) {
    this.code = code;
  }

  /**
   * @return the numeric status code
   */
  public int code() {
    return code;
  }
}
