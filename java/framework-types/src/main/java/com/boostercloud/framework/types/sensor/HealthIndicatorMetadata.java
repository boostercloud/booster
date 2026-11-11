package com.boostercloud.framework.types.sensor;

/**
 * What {@code @HealthSensor} registers in {@code config.userHealthIndicators[id]}. Port of
 * {@code HealthIndicatorMetadata}.
 *
 * @param type the indicator class (a {@link HealthIndicatorInterface})
 * @param healthIndicatorConfiguration its settings
 */
public record HealthIndicatorMetadata(Class<?> type, HealthIndicatorConfiguration healthIndicatorConfiguration) {}
