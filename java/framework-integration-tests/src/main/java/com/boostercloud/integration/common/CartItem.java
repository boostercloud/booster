package com.boostercloud.integration.common;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * A cart line ({@code src/common/cart-item.ts}); GraphQL type {@code CartItem}.
 *
 * @param productId the product
 * @param quantity how many units
 */
public record CartItem(UUID productId, double quantity) {}
