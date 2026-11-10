package com.boostercloud.framework.types.concepts;

/**
 * One schema migration ({@code @ToVersion}). Port of {@code SchemaMigrationMetadata}; stored in
 * {@code config.schemaMigrations[conceptName]} keyed by {@code toVersion}.
 *
 * @param migrationClass the class declaring the migration method
 * @param methodName the migration method name
 * @param toVersion the version it migrates to (greater than 1)
 * @param fromSchema the old schema class
 * @param toSchema the new schema class
 */
public record SchemaMigrationMetadata(
    Class<?> migrationClass, String methodName, int toVersion, Class<?> fromSchema, Class<?> toSchema) {}
