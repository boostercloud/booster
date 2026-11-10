package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The result of the indicators whose details come verbatim from the provider ({@code booster/database/events},
 * {@code booster/database/readmodels}). TypeScript types {@code HealthIndicatorResult.details} as an object but those
 * indicators store whatever the provider returns there (Azure returns an <b>array</b> of containers), so the details
 * are any JSON value here instead of the {@code Map} of {@link HealthIndicatorResult}. The health service serialises
 * both types to the same JSON shape ({@code { status, details }}).
 *
 * @param status the status
 * @param details the provider details (any JSON-serialisable value), or {@code null} for none
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"status", "details"})
public record RawHealthIndicatorResult(HealthStatus status, Object details) {}
