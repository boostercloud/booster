package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Version 1 of {@link ProductCreated}, as stored by older versions of the application.
 *
 * @param productId the product
 * @param sku the stock keeping unit
 * @param displayName the name shown to customers
 * @param price the price
 */
public record ProductCreatedV1(UUID productId, String sku, String displayName, double price) {}
