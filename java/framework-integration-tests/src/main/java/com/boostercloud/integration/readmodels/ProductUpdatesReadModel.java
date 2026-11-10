package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Product;
import com.boostercloud.integration.roles.Admin;
import java.time.Instant;

/**
 * When each product last changed ({@code src/read-models/product-updates-read-model.ts}); admins only.
 *
 * @param id the product
 * @param availability its availability
 * @param lastUpdate when it was last projected
 * @param previousUpdate the previous {@code lastUpdate}
 */
@ReadModel(authorize = Admin.class)
public record ProductUpdatesReadModel(UUID id, double availability, Instant lastUpdate, @Nullable Instant previousUpdate) {

  /**
   * @param product the product
   * @param previous the current read model, or {@code null}
   * @param projectionInfo why it is called
   * @return the read model, or a deletion when the product's event was deleted
   */
  @Projects(entity = Product.class, joinKey = "id", unProject = "updateWithProduct")
  public static ProjectionResult<ProductUpdatesReadModel> updateWithProduct(
      Product product, ProductUpdatesReadModel previous, ProjectionInfo projectionInfo) {
    if (projectionInfo != null && projectionInfo.reason() == ProjectionInfoReason.ENTITY_DELETED) {
      return ProjectionResult.delete();
    }
    return ProjectionResult.of(new ProductUpdatesReadModel(product.id(), product.availability(), Dates.now(),
        previous == null ? null : previous.lastUpdate()));
  }
}
