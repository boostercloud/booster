package com.example.cartdemo.common;

import com.boostercloud.framework.types.concepts.UUID;

/** A product and how many units of it are in a cart. */
public record CartItem(UUID productId, double quantity) {}
