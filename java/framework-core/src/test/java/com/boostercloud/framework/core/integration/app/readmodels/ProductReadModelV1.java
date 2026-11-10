package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Version 1 of {@link ProductReadModel}, as stored by older versions of the application.
 *
 * @param id the product
 * @param sku the stock keeping unit
 * @param displayName the name
 * @param price the price
 */
public record ProductReadModelV1(UUID id, String sku, String displayName, double price) {}
