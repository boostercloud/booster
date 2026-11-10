package com.boostercloud.rocket.fileuploads.azure.storage;

/**
 * A failed Blob Storage request ({@code RestError} of the Azure SDK): the HTTP status, the storage error code
 * ({@code x-ms-error-code} or the {@code <Code>} of the error body) and the service message.
 */
public class StorageError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;
  private final String errorCode;

  /**
   * @param statusCode the HTTP status
   * @param errorCode the storage error code, or {@code null}
   * @param message the message
   */
  public StorageError(int statusCode, String errorCode, String message) {
    super(message);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }

  /**
   * @return the HTTP status
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * @return the storage error code ({@code BlobNotFound}, {@code AuthorizationPermissionMismatch}...), or {@code null}
   */
  public String errorCode() {
    return errorCode;
  }
}
