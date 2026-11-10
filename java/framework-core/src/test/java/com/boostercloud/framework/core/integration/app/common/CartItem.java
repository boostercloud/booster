package com.boostercloud.framework.core.integration.app.common;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * A line of a cart.
 *
 * @param productId the product
 * @param quantity how many units
 */
public record CartItem(UUID productId, int quantity) {}
