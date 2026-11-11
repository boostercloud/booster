package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * Base of every Booster error: an unchecked exception with a {@code code} and optional {@code data}. Port of the
 * TypeScript {@code BoosterError}.
 *
 * <ul>
 *   <li>{@link #code()} defaults to the simple name of the runtime class ({@code new NotFoundError("x").code()} is
 *       {@code "NotFoundError"}; a user subclass {@code MyErr} gets {@code "MyErr"}). An explicit code is kept, even
 *       the empty string (TypeScript {@code code ?? this.constructor.name}).
 *   <li>{@code code} and {@code data} become {@code extensions.code} / {@code extensions.data} of GraphQL errors.
 *   <li>Serialized with {@code BoosterJson} it is {@code {"data": ..., "code": ...}} (no message), like
 *       {@code JSON.stringify}.
 * </ul>
 *
 * <p>Applications can throw it directly with their own code and data:
 * {@code throw new BoosterError("Cart is full", "CART_FULL", Map.of("max", 10))}.
 */
public class BoosterError extends RuntimeException {

  private final String code;
  private final Map<String, Object> data;

  /**
   * @param message the error message
   */
  public BoosterError(String message) {
    this(message, null, null, null);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public BoosterError(String message, String code) {
    this(message, code, null, null);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data sent to the client, or {@code null}
   */
  public BoosterError(String message, String code, Map<String, Object> data) {
    this(message, code, data, null);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public BoosterError(String message, Throwable cause) {
    this(message, null, null, cause);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data sent to the client, or {@code null}
   * @param cause the underlying cause, or {@code null}
   */
  public BoosterError(String message, String code, Map<String, Object> data, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.data = data;
  }

  /**
   * @return the explicit code, or the simple name of the runtime class
   */
  public String code() {
    return code != null ? code : getClass().getSimpleName();
  }

  /**
   * @return the extra data, or {@code null}
   */
  public Map<String, Object> data() {
    return data;
  }
}
