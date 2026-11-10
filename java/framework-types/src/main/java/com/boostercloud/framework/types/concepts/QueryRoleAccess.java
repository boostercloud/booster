package com.boostercloud.framework.types.concepts;

/**
 * Port of the TypeScript {@code QueryRoleAccess} option of {@code @Query}.
 *
 * @param authorize the authorization option; {@code null} (TypeScript: omitted) denies access
 */
public record QueryRoleAccess(Authorize<QueryAuthorizer> authorize) {}
