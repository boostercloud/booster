package com.boostercloud.integration.migrations.events.productupdated;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import java.util.List;

/**
 * The product embedded in the first versions of {@code ProductUpdated} (the module-private {@code interface Product}
 * of {@code src/migrations/events/ProductUpdated/schema-versions.ts}); not the {@code Product} entity, so that the old
 * schemas do not depend on it.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted
 */
public record Product(UUID id, String sku, String name, String description, Money price, List<Picture> pictures,
    boolean deleted) {}
