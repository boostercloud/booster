package com.boostercloud.framework.core.integration.app.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.searcher.SortFor;
import java.util.List;
import java.util.Map;

/**
 * The SKUs of a category, sorted (admins only), read with the read model searcher.
 *
 * @param category the category
 */
@Query(authorize = Admin.class)
public record ProductsInCategory(String category) {
  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the SKUs
   */
  public static List<String> handle(ProductsInCategory query, QueryInfo queryInfo) {
    return Booster.readModel(ProductReadModel.class)
        .filter(Map.of("category", Map.of("eq", query.category())))
        .sortBy(SortFor.of("sku", "ASC"))
        .search()
        .stream()
        .map(ProductReadModel::sku)
        .toList();
  }
}
