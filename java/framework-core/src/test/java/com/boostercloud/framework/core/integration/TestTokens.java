package com.boostercloud.framework.core.integration;

import com.boostercloud.framework.core.services.tokenverifiers.JwtTestKeys;
import com.boostercloud.framework.core.services.tokenverifiers.PemKeys;
import com.boostercloud.framework.types.Dates;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real RS256 tokens for the integration tests: one key pair whose public key the fixture application trusts
 * ({@code PublicKeyTokenVerifier} with {@link #ISSUER} and the {@link #ROLES_CLAIM} claim), and one it does not.
 */
public final class TestTokens {

  /** The issuer the application accepts. */
  public static final String ISSUER = "booster-integration-tests";

  /** The claim holding the roles. */
  public static final String ROLES_CLAIM = "booster:role";

  private static final KeyPair TRUSTED = JwtTestKeys.generate();
  private static final KeyPair UNTRUSTED = JwtTestKeys.generate();

  private TestTokens() {}

  /**
   * @return the trusted public key, as PEM
   */
  public static String publicKeyPem() {
    return PemKeys.toPem(TRUSTED.getPublic());
  }

  /**
   * @return a token of {@code admin@store.test} with the {@code Admin} role
   */
  public static String admin() {
    return token("admin-1", "admin@store.test", List.of("Admin"));
  }

  /**
   * @return a token of {@code customer@store.test} with the {@code Customer} role
   */
  public static String customer() {
    return token("customer-1", "customer@store.test", List.of("Customer"));
  }

  /**
   * @param subject the {@code sub} claim
   * @param email the {@code email} claim
   * @param roles the roles
   * @return a valid token for an hour
   */
  public static String token(String subject, String email, List<String> roles) {
    return sign(claims(subject, email, roles, Dates.nowEpochSeconds() + 3600, ISSUER), TRUSTED);
  }

  /**
   * @return a customer token that expired a minute ago
   */
  public static String expiredCustomer() {
    return sign(claims("customer-1", "customer@store.test", List.of("Customer"), Dates.nowEpochSeconds() - 60, ISSUER),
        TRUSTED);
  }

  /**
   * @return an admin token signed with a key the application does not trust
   */
  public static String forgedAdmin() {
    return sign(claims("admin-1", "admin@store.test", List.of("Admin"), Dates.nowEpochSeconds() + 3600, ISSUER),
        UNTRUSTED);
  }

  /**
   * @return an admin token from another issuer
   */
  public static String adminFromAnotherIssuer() {
    return sign(claims("admin-1", "admin@store.test", List.of("Admin"), Dates.nowEpochSeconds() + 3600,
        "somebody-else"), TRUSTED);
  }

  private static Map<String, Object> claims(String subject, String email, List<String> roles, long expiration,
      String issuer) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", subject);
    claims.put("email", email);
    claims.put(ROLES_CLAIM, roles);
    claims.put("iss", issuer);
    claims.put("iat", Dates.nowEpochSeconds() - 120);
    claims.put("exp", expiration);
    return claims;
  }

  private static String sign(Map<String, Object> claims, KeyPair keys) {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "RS256");
    header.put("typ", "JWT");
    return JwtTestKeys.sign(header, claims, keys.getPrivate());
  }
}
