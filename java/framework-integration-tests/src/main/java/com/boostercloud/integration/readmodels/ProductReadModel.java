package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Pack;
import com.boostercloud.integration.entities.Product;
import com.boostercloud.integration.entities.ProductType;
import com.boostercloud.integration.roles.UserWithEmail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The catalog view of a product ({@code src/read-models/product-read-model.ts}), only for users with the
 * {@code UserWithEmail} role; schema version 2 (see {@code migrations.readmodels.productreadmodel}). It projects the
 * product (deleted products remove it) and the packs that contain it (array join key {@code Pack.products}).
 *
 * @param id the product
 * @param sku its SKU
 * @param displayName its name
 * @param description its description
 * @param availability units available
 * @param deleted whether it was deleted
 * @param productDetails free-form details
 * @param productType one of {@link ProductType} ({@code 'Other'} by default)
 * @param price its price
 * @param packs the packs that contain it
 */
@ReadModel(authorize = UserWithEmail.class)
public record ProductReadModel(UUID id, String sku, String displayName, String description, double availability,
    boolean deleted, Map<String, Object> productDetails, Object productType, @Nullable Money price,
    @Nullable List<Pack> packs) {

  /** Applies the {@code productType} default. */
  public ProductReadModel {
    productType = ProductType.orDefault(productType);
  }

  /**
   * @param product the product
   * @return the read model with no packs, or a deletion for deleted products
   */
  @Projects(entity = Product.class, joinKey = "id", unProject = "unProjectWithProduct")
  public static ProjectionResult<ProductReadModel> updateWithProduct(Product product) {
    if (product.deleted()) {
      return ProjectionResult.delete();
    }
    return ProjectionResult.of(new ProductReadModel(product.id(), product.sku(), product.displayName(),
        product.description(), product.availability(), product.deleted(), product.productDetails(),
        product.productType(), product.price(), new ArrayList<>()));
  }

  /**
   * @param product the product whose event was deleted
   * @param currentProductReadModel the current read model, or {@code null}
   * @param projectionInfo why it is called
   * @return a deletion
   */
  public static ProjectionResult<ProductReadModel> unProjectWithProduct(
      Product product, ProductReadModel currentProductReadModel, ProjectionInfo projectionInfo) {
    return ProjectionResult.delete();
  }

  /**
   * Adds the pack to (or replaces it in) the packs of an existing product read model.
   *
   * @param pack the pack
   * @param readModelID one of its products
   * @param currentProductReadModel the current read model, or {@code null}
   * @param projectionInfo why it is called
   * @return the read model with the pack; a deletion when the pack's event was deleted
   */
  @Projects(entity = Pack.class, joinKey = "products", unProject = "updateWithPack")
  public static ProjectionResult<ProductReadModel> updateWithPack(Pack pack, UUID readModelID,
      ProductReadModel currentProductReadModel, ProjectionInfo projectionInfo) {
    if (projectionInfo != null && projectionInfo.reason() == ProjectionInfoReason.ENTITY_DELETED) {
      return ProjectionResult.delete();
    }
    if (currentProductReadModel == null) {
      return ProjectionResult.nothing();
    }
    List<Pack> packList = currentProductReadModel.packs() == null
        ? new ArrayList<>() : new ArrayList<>(currentProductReadModel.packs());
    if (packList.stream().anyMatch(existing -> existing.id().equals(pack.id()))) {
      packList.replaceAll(existing -> existing.id().equals(pack.id()) ? pack : existing);
    } else {
      packList.add(pack);
    }
    return ProjectionResult.of(new ProductReadModel(currentProductReadModel.id(), currentProductReadModel.sku(),
        currentProductReadModel.displayName(), currentProductReadModel.description(),
        currentProductReadModel.availability(), currentProductReadModel.deleted(),
        currentProductReadModel.productDetails(), currentProductReadModel.productType(),
        currentProductReadModel.price(), packList));
  }
}
