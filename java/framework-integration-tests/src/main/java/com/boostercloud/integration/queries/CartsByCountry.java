package com.boostercloud.integration.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.integration.readmodels.CartReadModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The carts with a shipping country, grouped by country ({@code src/queries/carts-by-country.ts}); the result is a
 * {@code JSON} object.
 */
@Query(authorize = All.class)
public record CartsByCountry() {

  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the carts of each country
   */
  public static Map<String, List<CartReadModel>> handle(CartsByCountry query, QueryInfo queryInfo) {
    List<CartReadModel> carts = Booster.readModel(CartReadModel.class)
        .filter(Map.of("shippingAddress", Map.of("country", Map.of("isDefined", true))))
        .paginatedVersion(false)
        .search();
    Map<String, List<CartReadModel>> group = new LinkedHashMap<>();
    for (CartReadModel cartReadModel : carts) {
      String country = cartReadModel.shippingAddress() == null || cartReadModel.shippingAddress().country() == null
          ? "" : cartReadModel.shippingAddress().country();
      group.computeIfAbsent(country, key -> new ArrayList<>()).add(cartReadModel);
    }
    return group;
  }
}
