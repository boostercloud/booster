package com.boostercloud.framework.types.concepts;

/**
 * What {@code @Entity} registers in {@code config.entities[className]}. Port of {@code EntityMetadata}.
 *
 * @param type the entity class
 * @param eventStreamAuthorizer authorizer for reading the entity's events (deny by default)
 */
public record EntityMetadata(Class<?> type, EventStreamAuthorizer eventStreamAuthorizer) {}
