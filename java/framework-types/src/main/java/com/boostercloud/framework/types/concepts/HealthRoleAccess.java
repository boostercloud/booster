package com.boostercloud.framework.types.concepts;

/**
 * Port of the TypeScript {@code HealthRoleAccess} option of the health sensor ({@code sensorConfiguration.health.globalAuthorizer}).
 *
 * @param authorize the authorization option; {@code null} (TypeScript: omitted) denies access
 */
public record HealthRoleAccess(Authorize<HealthAuthorizer> authorize) {
  /**
   * @return {@code { authorize: 'all' }}, the default of the health sensor
   */
  public static HealthRoleAccess all() {
    return new HealthRoleAccess(Authorize.all());
  }
}
