package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code TokenVerifier}: the JWT verifiers of {@code config.tokenVerifiers}. Core runs all verifiers for a
 * token and takes the first one, in list order, that succeeds.
 */
public interface TokenVerifier {
  /**
   * Verifies a token.
   *
   * @param token the raw token (without the {@code Bearer } prefix)
   * @return the decoded token
   * @throws RuntimeException when the token is not valid for this verifier
   */
  DecodedToken verify(String token);

  /**
   * @param decodedToken a token returned by {@link #verify(String)}
   * @return the user it represents
   */
  UserEnvelope toUserEnvelope(DecodedToken decodedToken);
}
