package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.core.integration.app.entities.Product;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.core.integration.app.roles.Customer;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The catalogue entry of a product, for admins and customers (schema version 2: version 1,
 * {@link ProductReadModelV1}, had no category).
 *
 * @param id the product
 * @param sku the stock keeping unit
 * @param displayName the name
 * @param price the price
 * @param category the category
 */
@ReadModel(authorize = {Admin.class, Customer.class})
public record ProductReadModel(UUID id, String sku, String displayName, double price, String category) {

  /**
   * @param product the product
   * @param current the current read model, or {@code null}
   * @return the read model, or delete it when the product was withdrawn
   */
  @Projects(entity = Product.class, joinKey = "id")
  public static ProjectionResult<ProductReadModel> project(Product product, ProductReadModel current) {
    if (product.deleted()) {
      return ProjectionResult.delete();
    }
    return ProjectionResult.of(new ProductReadModel(product.id(), product.sku(), product.displayName(),
        product.price(), product.category()));
  }
}
