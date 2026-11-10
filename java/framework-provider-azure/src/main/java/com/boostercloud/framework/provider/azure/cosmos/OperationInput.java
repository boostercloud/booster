package com.boostercloud.framework.provider.azure.cosmos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * A transactional batch operation ({@code OperationInput} of {@code @azure/cosmos}).
 *
 * @param operationType {@code Create}, {@code Upsert}, {@code Replace}, {@code Read} or {@code Delete}
 * @param resourceBody the document for write operations
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"operationType", "resourceBody"})
public record OperationInput(String operationType, Map<String, Object> resourceBody) {

  /**
   * @param resourceBody the document
   * @return a {@code Create} operation ({@code { operationType: 'Create', resourceBody }})
   */
  public static OperationInput create(Map<String, Object> resourceBody) {
    return new OperationInput("Create", resourceBody);
  }
}
