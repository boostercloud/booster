package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.DataMigrationParameters;

/**
 * What {@code @DataMigration} registers in {@code config.dataMigrationHandlers[className]}.
 *
 * @param type the data migration class
 * @param migrationOptions its options ({@code order})
 */
public record DataMigrationMetadata(Class<?> type, DataMigrationParameters migrationOptions) {}
