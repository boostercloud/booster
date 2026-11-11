package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.core.integration.app.entities.Product;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * The products of a category: many entities projected into one read model (whose id is the category name), which
 * makes concurrent projections conflict and retry.
 *
 * @param id the category
 * @param productIds the products in it, in projection order
 */
@ReadModel(authorize = All.class)
public record CategoryReadModel(UUID id, List<UUID> productIds) {

  /**
   * @param product the product
   * @param current the category, or {@code null}
   * @return the category with or without the product
   */
  @Projects(entity = Product.class, joinKey = "category")
  public static ProjectionResult<CategoryReadModel> project(Product product, CategoryReadModel current) {
    List<UUID> productIds = new ArrayList<>(current == null ? List.of() : current.productIds());
    productIds.remove(product.id());
    if (!product.deleted()) {
      productIds.add(product.id());
    }
    return ProjectionResult.of(new CategoryReadModel(UUID.of(product.category()), productIds));
  }
}
