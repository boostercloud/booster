package com.boostercloud.framework.types.concepts;

/**
 * Port of the TypeScript {@code ReadModelRoleAccess} option of {@code @ReadModel}.
 *
 * @param authorize the authorization option; {@code null} (TypeScript: omitted) denies access
 */
public record ReadModelRoleAccess(Authorize<ReadModelAuthorizer> authorize) {}
