package com.boostercloud.framework.provider.azure.infrastructure.azure;

/**
 * An Azure Resource Manager request failed ({@code RestError} of {@code @azure/core-rest-pipeline}): the message and
 * code come from the ARM error body ({@code {"error": {"code": "ResourceGroupNotFound", "message": "..."}}}).
 */
public class ArmError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;
  private final String code;

  /**
   * @param statusCode the HTTP status (0 when the failure is a failed long-running operation)
   * @param code the ARM error code, or {@code null}
   * @param message the message
   */
  public ArmError(int statusCode, String code, String message) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
  }

  /**
   * @return the HTTP status
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * @return the ARM error code, or {@code null}
   */
  public String code() {
    return code;
  }
}
