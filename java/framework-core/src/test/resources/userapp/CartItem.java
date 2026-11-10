package com.example.store.common;

import com.boostercloud.framework.types.concepts.UUID;

public record CartItem(UUID productId, double quantity) {}
