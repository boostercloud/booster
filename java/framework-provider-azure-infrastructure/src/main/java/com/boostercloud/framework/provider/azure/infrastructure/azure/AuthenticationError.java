package com.boostercloud.framework.provider.azure.infrastructure.azure;

/** Microsoft Entra ID refused the credentials ({@code AuthenticationError} of {@code @azure/identity}). */
public class AuthenticationError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;

  /**
   * @param statusCode the HTTP status of the token endpoint (0 for transport failures)
   * @param message the message
   * @param cause the cause, or {@code null}
   */
  public AuthenticationError(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * @return the HTTP status of the token endpoint
   */
  public int statusCode() {
    return statusCode;
  }
}
