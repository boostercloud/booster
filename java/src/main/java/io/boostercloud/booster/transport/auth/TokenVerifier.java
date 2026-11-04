package io.boostercloud.booster.transport.auth;

/** Verifies a compact signed token. Implementations never trust token-supplied key URLs. */
@FunctionalInterface
public interface TokenVerifier {
    VerifiedToken verify(String token);
}
