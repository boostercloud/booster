package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/token-verifiers/utilities.ts}: the JWT/JWKS primitives ({@code getJwksClient},
 * {@code getKeyWithClient}, {@code verifyJWT}), implemented with the JDK (see {@link JwksClient} and the class
 * documentation of the verification rules in {@code verifyJWT}).
 */
public final class Utilities {

  /** The key cache lifetime of {@link #getJwksClient(String)}: 15 minutes. */
  public static final long JWKS_CACHE_MAX_AGE = 15 * 60 * 1000;

  /** The only accepted signing algorithm. */
  public static final List<String> ALGORITHMS = List.of("RS256");

  private Utilities() {}

  /**
   * {@code getJwksClient(jwksUri)}: a client with {@code cache: true} and {@code cacheMaxAge: 900000}.
   *
   * @param jwksUri the JWKS URL
   * @return the client
   */
  public static JwksClient getJwksClient(String jwksUri) {
    return new JwksClient(new JwksClient.Options(jwksUri, true, JWKS_CACHE_MAX_AGE));
  }

  /**
   * {@code getKeyWithClient(client, header, callback)}: the PEM public key matching the header's {@code kid}. The
   * TypeScript callback becomes a return value (or an exception).
   *
   * @param client the JWKS client
   * @param header the JWT header
   * @return the PEM public key
   * @throws IllegalStateException {@code JWT kid not found} when the header has no {@code kid}; the client's error
   *     otherwise
   */
  public static String getKeyWithClient(JwksClient client, Map<String, Object> header) {
    Object kid = header == null ? null : header.get("kid");
    if (!Js.isTruthy(kid)) {
      throw new IllegalStateException("JWT kid not found");
    }
    return client.getSigningKey(kid.toString()).getPublicKey();
  }

  /**
   * {@code verifyJWT(token, issuer, key)} with a fixed key: PEM text (public key, certificate or RSA private key;
   * other text is a "secret", which RS256 rejects) or a {@link java.security.PublicKey}.
   *
   * @param token the token; the first {@code "Bearer "} is removed
   * @param issuer the expected {@code iss}
   * @param key the key
   * @return the decoded token
   * @throws JsonWebTokenError (or {@link TokenExpiredError}/{@link NotBeforeError}) when it does not verify
   */
  public static DecodedToken verifyJWT(String token, String issuer, Object key) {
    return JwtVerifier.verify(sanitize(token), key, issuer, ALGORITHMS);
  }

  /**
   * {@code verifyJWT(token, issuer, keyResolver)}: the key is computed from the header.
   *
   * @param token the token; the first {@code "Bearer "} is removed
   * @param issuer the expected {@code iss}
   * @param key the key resolver
   * @return the decoded token
   */
  public static DecodedToken verifyJWT(String token, String issuer, KeyResolver key) {
    return JwtVerifier.verify(sanitize(token), key, issuer, ALGORITHMS);
  }

  /** {@code token.replace('Bearer ', '')}: removes the first occurrence only. */
  private static String sanitize(String token) {
    if (token == null) {
      return null;
    }
    int index = token.indexOf("Bearer ");
    return index < 0 ? token : token.substring(0, index) + token.substring(index + "Bearer ".length());
  }
}
