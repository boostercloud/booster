package com.boostercloud.framework.core.services.tokenverifiers;

import java.util.Map;

/**
 * The key-resolver form of {@code jsonwebtoken}'s key argument ({@code GetPublicKeyOrSecret}): computes the
 * verification key from the token header, e.g. by {@code kid}. A failure is reported as
 * {@code error in secret or public key callback: <message>}.
 */
@FunctionalInterface
public interface KeyResolver {
  /**
   * @param header the decoded JWT header
   * @return the key: PEM text, or a {@link java.security.PublicKey}
   */
  Object resolve(Map<String, Object> header);
}
