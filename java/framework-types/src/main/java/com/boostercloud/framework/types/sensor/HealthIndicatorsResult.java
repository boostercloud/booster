package com.boostercloud.framework.types.sensor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * A node of the health response tree. Port of {@code HealthIndicatorsResult}.
 *
 * @param status the status
 * @param details free-form details, optional
 * @param name the indicator name
 * @param id the indicator id (a slash-separated path such as {@code booster/database/events})
 * @param components the children, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"status", "name", "id", "details", "components"})
public record HealthIndicatorsResult(
    HealthStatus status, Map<String, Object> details, String name, String id, List<HealthIndicatorsResult> components) {}
