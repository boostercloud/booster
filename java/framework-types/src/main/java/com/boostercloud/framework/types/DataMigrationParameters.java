package com.boostercloud.framework.types;

/**
 * Port of {@code data-migration-parameters.ts}: the options of {@code @DataMigration}.
 *
 * @param order execution order; data migrations run in ascending order
 */
public record DataMigrationParameters(int order) {}
