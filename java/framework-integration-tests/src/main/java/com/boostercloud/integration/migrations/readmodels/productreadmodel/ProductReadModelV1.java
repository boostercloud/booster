package com.boostercloud.integration.migrations.readmodels.productreadmodel;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import java.util.List;

/**
 * Version 1 of {@code ProductReadModel} ({@code src/migrations/read-models/ProductReadModel/schema-versions.ts}):
 * {@code name} instead of {@code displayName}, pictures and no availability, details, type or packs. The current
 * version (TypeScript's {@code ProductReadModelV2 extends ProductReadModel}) is {@code ProductReadModel} itself,
 * since records cannot be extended.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted ({@code false} by default)
 */
public record ProductReadModelV1(UUID id, String sku, String name, String description, Money price,
    List<Picture> pictures, boolean deleted) {}
