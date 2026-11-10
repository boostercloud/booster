package com.boostercloud.framework.types.sensor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * What a health indicator returns. Port of {@code HealthIndicatorResult}.
 *
 * @param status the status
 * @param details free-form details, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record HealthIndicatorResult(HealthStatus status, Map<String, Object> details) {
  /**
   * @param status the status
   * @return a result without details
   */
  public static HealthIndicatorResult of(HealthStatus status) {
    return new HealthIndicatorResult(status, null);
  }
}
