package com.boostercloud.framework.provider.azure.cosmos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A query parameter ({@code SqlParameter} of {@code @azure/cosmos}). A {@code null} value is sent as JSON
 * {@code null} (filters such as {@code eq: null} rely on it).
 *
 * @param name the placeholder, with its {@code @}
 * @param value the raw value
 */
@JsonPropertyOrder({"name", "value"})
public record SqlParameter(String name, @JsonInclude(JsonInclude.Include.ALWAYS) Object value) {

  /**
   * @param name the placeholder
   * @param value the value
   * @return the parameter
   */
  public static SqlParameter of(String name, Object value) {
    return new SqlParameter(name, value);
  }
}
