package com.boostercloud.framework.types.concepts;

import java.util.List;

/**
 * Port of {@code AuthMetadata}: sign-up settings of a role (only used by legacy provider auth infrastructure).
 *
 * @param signUpMethods allowed sign-up methods, or {@code null}
 * @param skipConfirmation whether to skip the confirmation step, or {@code null}
 */
public record AuthMetadata(List<SignUpMethod> signUpMethods, Boolean skipConfirmation) {
  /** Canonical constructor; copies the list. */
  public AuthMetadata {
    signUpMethods = signUpMethods == null ? null : List.copyOf(signUpMethods);
  }
}
