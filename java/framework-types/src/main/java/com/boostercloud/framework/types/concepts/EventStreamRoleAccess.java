package com.boostercloud.framework.types.concepts;

/**
 * Port of the TypeScript {@code EventStreamRoleAccess} option of {@code @Entity} (reading the entity's event stream).
 *
 * @param authorizeReadEvents the authorization option; {@code null} (TypeScript: omitted) denies access
 */
public record EventStreamRoleAccess(Authorize<EventStreamAuthorizer> authorizeReadEvents) {}
