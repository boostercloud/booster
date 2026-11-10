package com.boostercloud.framework.types.concepts;

/**
 * Port of the TypeScript {@code CommandRoleAccess} option of {@code @Command}.
 *
 * @param authorize the authorization option; {@code null} (TypeScript: omitted) denies access
 */
public record CommandRoleAccess(Authorize<CommandAuthorizer> authorize) {}
