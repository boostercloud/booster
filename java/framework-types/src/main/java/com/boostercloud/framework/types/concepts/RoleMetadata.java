package com.boostercloud.framework.types.concepts;

/**
 * What {@code @Role} registers in {@code config.roles[className]}. Port of {@code RoleMetadata}.
 *
 * @param auth sign-up settings (default {@code {}})
 */
public record RoleMetadata(AuthMetadata auth) {
  /** Canonical constructor; {@code null} becomes an empty {@link AuthMetadata}. */
  public RoleMetadata {
    auth = auth == null ? new AuthMetadata(null, null) : auth;
  }

  /**
   * @return {@code { auth: {} }}, the default of {@code @Role()}
   */
  public static RoleMetadata defaults() {
    return new RoleMetadata(new AuthMetadata(null, null));
  }
}
