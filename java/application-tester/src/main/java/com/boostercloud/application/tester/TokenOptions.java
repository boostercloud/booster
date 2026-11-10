package com.boostercloud.application.tester;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options of {@link TokenHelper#forUser(String, String, TokenOptions)}. Port of the TypeScript
 * {@code TokenOptions} type ({@code token-helper.ts}); {@code null} means "not given" ({@code undefined}).
 *
 * <ul>
 *   <li>{@code expiresIn}: seconds after {@code iat} for {@code exp}. Applied whenever it is not {@code null}, so
 *       {@code 0} produces a token that is already expired.
 *   <li>{@code notBefore}: seconds after {@code iat} for {@code nbf}. Applied only when it is not {@code null} and not
 *       {@code 0} (TypeScript checks it for truthiness).
 *   <li>{@code customClaims}: extra payload claims, merged after {@code id}/{@code email} (so they can override
 *       them) and before {@code booster:role}. The insertion order of the map is kept in the payload.
 * </ul>
 *
 * @param expiresIn seconds until expiration, relative to {@code iat}
 * @param notBefore seconds until the token is valid, relative to {@code iat}
 * @param customClaims extra claims (any JSON-serialisable values, {@code null} values included)
 */
public record TokenOptions(Long expiresIn, Long notBefore, Map<String, Object> customClaims) {

  private static final TokenOptions NONE = new TokenOptions(null, null, null);

  /** Canonical constructor; the claims are copied keeping their order. */
  public TokenOptions {
    customClaims = customClaims == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(customClaims));
  }

  /**
   * @return options with nothing set ({@code {}})
   */
  public static TokenOptions none() {
    return NONE;
  }

  /**
   * @return a builder with nothing set
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return a builder initialised with these options
   */
  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.expiresIn = expiresIn;
    builder.notBefore = notBefore;
    if (customClaims != null) {
      builder.customClaims = new LinkedHashMap<>(customClaims);
    }
    return builder;
  }

  /** Builder of {@link TokenOptions}. */
  public static final class Builder {
    private Long expiresIn;
    private Long notBefore;
    private LinkedHashMap<String, Object> customClaims;

    private Builder() {}

    /**
     * @param seconds seconds until expiration ({@code 0} = already expired)
     * @return this builder
     */
    public Builder expiresIn(long seconds) {
      this.expiresIn = seconds;
      return this;
    }

    /**
     * @param seconds seconds until expiration ({@code 0} = already expired), {@code null} to unset
     * @return this builder
     */
    public Builder expiresIn(Long seconds) {
      this.expiresIn = seconds;
      return this;
    }

    /**
     * @param seconds seconds until the token becomes valid ({@code 0} is ignored when signing)
     * @return this builder
     */
    public Builder notBefore(long seconds) {
      this.notBefore = seconds;
      return this;
    }

    /**
     * @param seconds seconds until the token becomes valid ({@code 0} and {@code null} are ignored when signing)
     * @return this builder
     */
    public Builder notBefore(Long seconds) {
      this.notBefore = seconds;
      return this;
    }

    /**
     * Replaces the custom claims.
     *
     * @param claims the claims, {@code null} to unset
     * @return this builder
     */
    public Builder customClaims(Map<String, ?> claims) {
      this.customClaims = claims == null ? null : new LinkedHashMap<>(claims);
      return this;
    }

    /**
     * Adds (or replaces) one custom claim.
     *
     * @param name the claim name
     * @param value the claim value (may be {@code null})
     * @return this builder
     */
    public Builder customClaim(String name, Object value) {
      if (customClaims == null) {
        customClaims = new LinkedHashMap<>();
      }
      customClaims.put(name, value);
      return this;
    }

    /**
     * @return the options
     */
    public TokenOptions build() {
      return new TokenOptions(expiresIn, notBefore, customClaims);
    }
  }
}
