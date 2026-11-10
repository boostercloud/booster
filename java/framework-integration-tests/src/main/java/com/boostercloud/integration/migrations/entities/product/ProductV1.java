package com.boostercloud.integration.migrations.entities.product;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import java.util.List;

/**
 * Version 1 of the {@code Product} entity ({@code src/migrations/entities/Product/schema-versions.ts}): {@code name}
 * instead of {@code displayName}, no availability, details or type. The current version (TypeScript's
 * {@code ProductV2 extends Product}) is {@code Product} itself, since records cannot be extended.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted ({@code false} by default)
 */
public record ProductV1(UUID id, String sku, String name, String description, Money price, List<Picture> pictures,
    boolean deleted) {}
