package com.boostercloud.framework.core.integration.app.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.core.integration.app.readmodels.CartReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the carts and products with an id (a query returning a list of a union type).
 *
 * @param id the id
 */
@Query(authorize = All.class)
public record FindStoreItem(UUID id) {
  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the items found
   */
  public static List<StoreItem> handle(FindStoreItem query, QueryInfo queryInfo) {
    List<StoreItem> found = new ArrayList<>();
    Map<String, Object> byId = Map.of("id", Map.of("eq", query.id()));
    Booster.readModel(CartReadModel.class).filter(byId).searchOne()
        .ifPresent(cart -> found.add(new StoreItem.CartView(cart.id(), cart.totalQuantity())));
    Booster.readModel(ProductReadModel.class).filter(byId).searchOne()
        .ifPresent(product -> found.add(new StoreItem.ProductView(product.id(), product.sku())));
    return found;
  }
}
