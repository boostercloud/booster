package com.boostercloud.integration.entities;

import com.boostercloud.integration.common.Money;

/**
 * A line of an {@link Order} ({@code export interface OrderItem} of {@code src/entities/order.ts}).
 *
 * @param product the product
 * @param quantity how many units
 * @param unitPrice the price of one unit
 */
public record OrderItem(Product product, double quantity, Money unitPrice) {}
