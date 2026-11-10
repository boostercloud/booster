package com.boostercloud.framework.types.concepts;

/**
 * Duration of a data migration, stored in the {@code BoosterDataMigrationEntity} snapshot. Port of
 * {@code BoosterDataMigrationEntityDuration}.
 *
 * @param start ISO start time
 * @param end ISO end time, when finished
 * @param elapsedMilliseconds {@code end - start} in milliseconds, when finished
 */
public record BoosterDataMigrationEntityDuration(String start, String end, Long elapsedMilliseconds) {}
