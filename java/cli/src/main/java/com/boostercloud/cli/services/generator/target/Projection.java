package com.boostercloud.cli.services.generator.target;

/**
 * A projection of an entity into a read model ({@code Projection}), written {@code <entity name>:<entity id>}.
 *
 * @param entityName the entity class name
 * @param entityId the property of the entity used as join key
 */
public record Projection(String entityName, String entityId) {}
