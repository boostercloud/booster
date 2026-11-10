package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Product;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every product of a SKU, most expensive first ({@code src/read-models/products-by-sku.ts}): many entities projected
 * into one read model (id = the SKU).
 *
 * <p>The entries are TypeScript's private {@code interface ProductIDWithPrice { productId, priceCents }}, which
 * GraphQL exposes as {@code JSON}; Java keeps them as maps ({@link #entry}) for the same schema.
 *
 * @param id the SKU
 * @param products the products of the SKU, by descending price
 * @param firstProduct the most expensive one
 * @param record {@code { items: <count> }}
 */
@ReadModel(authorize = All.class)
public record ProductsBySKU(UUID id, List<Map<String, Object>> products, @Nullable Map<String, Object> firstProduct,
    @Nullable Map<String, Double> record) {

  /** {@code products = []} by default. */
  public ProductsBySKU {
    products = products == null ? new ArrayList<>() : products;
  }

  /**
   * @param entity the product
   * @param currentProductsBySKU the current read model, or {@code null}
   * @param projectionInfo why it is called
   * @return the read model with the product's latest price, or a deletion when its event was deleted
   */
  @Projects(entity = Product.class, joinKey = "sku", unProject = "projectProduct")
  public static ProjectionResult<ProductsBySKU> projectProduct(
      Product entity, ProductsBySKU currentProductsBySKU, ProjectionInfo projectionInfo) {
    if (projectionInfo != null && projectionInfo.reason() == ProjectionInfoReason.ENTITY_DELETED) {
      return ProjectionResult.delete();
    }
    if (currentProductsBySKU == null) {
      currentProductsBySKU = new ProductsBySKU(UUID.of(entity.sku()), new ArrayList<>(), null, null);
    }
    String productId = entity.id().value();
    List<Map<String, Object>> newAndSortedProducts = new ArrayList<>(currentProductsBySKU.products().stream()
        .filter(product -> !Objects.equals(String.valueOf(product.get("productId")), productId))
        .toList());
    newAndSortedProducts.add(entry(productId, entity.price().cents()));
    // List.sort is stable, like Array.prototype.sort
    newAndSortedProducts.sort(Comparator.comparingDouble((Map<String, Object> product) -> priceCents(product))
        .reversed());
    Map<String, Double> record = new LinkedHashMap<>();
    record.put("items", (double) newAndSortedProducts.size());
    return ProjectionResult.of(new ProductsBySKU(currentProductsBySKU.id(), newAndSortedProducts,
        newAndSortedProducts.getFirst(), record));
  }

  /**
   * @param productId the product
   * @param priceCents its price
   * @return a {@code ProductIDWithPrice}
   */
  public static Map<String, Object> entry(String productId, double priceCents) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("productId", productId);
    entry.put("priceCents", priceCents);
    return entry;
  }

  private static double priceCents(Map<String, Object> product) {
    return product.get("priceCents") instanceof Number number ? number.doubleValue() : Double.NaN;
  }
}
