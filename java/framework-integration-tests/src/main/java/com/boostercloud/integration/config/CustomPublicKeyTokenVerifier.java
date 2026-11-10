package com.boostercloud.integration.config;

import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.framework.types.concepts.DecodedToken;

/**
 * A verifier that checks the token like {@link PublicKeyTokenVerifier} and then always rejects it with
 * {@code Unauthorized} (the private class of {@code src/config/config.ts}). Configured after a plain
 * {@link PublicKeyTokenVerifier}: valid tokens are accepted thanks to the first verifier (the first one that succeeds
 * wins) and invalid ones are rejected with the reasons of both.
 */
public class CustomPublicKeyTokenVerifier extends PublicKeyTokenVerifier {

  /**
   * @param issuer the expected issuer
   * @param publicKey the PEM public key
   * @param rolesClaim the claim holding the roles
   */
  public CustomPublicKeyTokenVerifier(String issuer, String publicKey, String rolesClaim) {
    super(issuer, publicKey, rolesClaim);
  }

  @Override
  public DecodedToken verify(String token) {
    super.verify(token);
    throw new IllegalStateException("Unauthorized");
  }
}
