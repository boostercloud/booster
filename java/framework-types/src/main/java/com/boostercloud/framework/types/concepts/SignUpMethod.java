package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Port of {@code SignUpMethod = 'email' | 'phone'}; serialized with the TypeScript literals. */
public enum SignUpMethod {
  /** {@code 'email'}. */
  EMAIL("email"),
  /** {@code 'phone'}. */
  PHONE("phone");

  private final String value;

  SignUpMethod(String value) {
    this.value = value;
  }

  /**
   * @return the TypeScript literal
   */
  @JsonValue
  public String value() {
    return value;
  }

  /**
   * @param value {@code "email"} or {@code "phone"} (case-insensitive)
   * @return the method
   */
  @JsonCreator
  public static SignUpMethod fromValue(String value) {
    for (SignUpMethod method : values()) {
      if (method.value.equalsIgnoreCase(value)) {
        return method;
      }
    }
    throw new IllegalArgumentException("Unknown sign-up method '" + value + "'");
  }
}
