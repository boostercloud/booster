package com.boostercloud.framework.core.services.tokenverifiers;

/**
 * Port of {@code JWT_ENV_VARS}: the environment variables of the legacy default {@link JwksUriTokenVerifier}, read by
 * {@code Booster.start} when all three are set.
 *
 * @deprecated [EOL v3] initialize the token verifiers explicitly in {@code config.tokenVerifiers}
 */
@Deprecated
public final class JwtEnvVars {

  /** {@code BOOSTER_JWT_ISSUER}. */
  public static final String BOOSTER_JWT_ISSUER = "BOOSTER_JWT_ISSUER";

  /** {@code BOOSTER_JWKS_URI}. */
  public static final String BOOSTER_JWKS_URI = "BOOSTER_JWKS_URI";

  /** {@code BOOSTER_ROLES_CLAIM}. */
  public static final String BOOSTER_ROLES_CLAIM = "BOOSTER_ROLES_CLAIM";

  private JwtEnvVars() {}
}
