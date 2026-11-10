package com.boostercloud.framework.core.services.tokenverifiers;

import java.security.PublicKey;
import java.util.Objects;

/**
 * Port of {@code jwks-rsa}'s {@code SigningKey}: one usable key of a JWKS document.
 *
 * @param kid the key id, or {@code null}
 * @param alg the declared algorithm, or {@code null}
 * @param publicKey the key
 */
public record SigningKey(String kid, String alg, PublicKey publicKey) {

  /**
   * @param kid the key id, or {@code null}
   * @param alg the declared algorithm, or {@code null}
   * @param publicKey the key
   */
  public SigningKey {
    Objects.requireNonNull(publicKey, "publicKey");
  }

  /**
   * {@code getPublicKey()}: the key as SPKI PEM text.
   *
   * @return the PEM text
   */
  public String getPublicKey() {
    return PemKeys.toPem(publicKey);
  }
}
