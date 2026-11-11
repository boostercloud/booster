package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.common.Picture;
import com.boostercloud.integration.events.ProductUpdateReason;
import com.boostercloud.integration.events.ProductUpdated;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Updates a product ({@code src/commands/update-product.ts}); schema version 2 (version 1 had a single
 * {@code description}, see {@code migrations.commands.updateproduct}). The event's description is the short and the
 * long description joined by a line feed. TypeScript's defaults ({@code deleted = false},
 * {@code reason = CatalogChange}) apply to missing values.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param shortDescription the first line of the description
 * @param longDescription the rest of the description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted
 * @param reason why it changes
 * @param productDetails free-form details
 * @param productType one of the {@code ProductType} values
 */
@Command(authorize = All.class)
public record UpdateProduct(UUID id, String sku, String name, String shortDescription, String longDescription,
    Money price, List<Picture> pictures, boolean deleted, ProductUpdateReason reason,
    @Nullable Map<String, Object> productDetails, @Nullable Object productType) {

  /** Applies the defaults. */
  public UpdateProduct {
    pictures = pictures == null ? new ArrayList<>() : pictures;
    reason = reason == null ? ProductUpdateReason.CatalogChange : reason;
  }

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(UpdateProduct command, Register register) {
    register.events(new ProductUpdated(command.id(), command.sku(), command.name(),
        command.shortDescription() + "\n" + command.longDescription(), command.price(), command.pictures(),
        command.deleted(), command.reason(), command.productDetails(), command.productType()));
  }
}
