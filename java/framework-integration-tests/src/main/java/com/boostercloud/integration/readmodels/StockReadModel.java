package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Stock;
import com.boostercloud.integration.roles.Admin;
import java.util.Map;

/**
 * The stock of a product per warehouse ({@code src/read-models/stock-read-model.ts}); admins only.
 *
 * @param id the product
 * @param warehouses the units per warehouse
 */
@ReadModel(authorize = Admin.class)
public record StockReadModel(UUID id, Map<String, Double> warehouses) {

  /**
   * @param entity the stock
   * @return the read model
   */
  @Projects(entity = Stock.class, joinKey = "id")
  public static ProjectionResult<StockReadModel> projectStock(Stock entity) {
    return ProjectionResult.of(new StockReadModel(entity.id(), entity.warehouses()));
  }
}
