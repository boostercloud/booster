package com.boostercloud.cli.services.generator.target;

/**
 * A field of a generated resource ({@code Field}), written {@code <field name>:<field type>}.
 *
 * @param name the field name
 * @param type the type exactly as given (TypeScript-style types are mapped to Java when rendering)
 */
public record Field(String name, String type) {}
