package com.boostercloud.integration.migrations.readmodels.cartreadmodel;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Payment;
import java.util.List;

/**
 * Version 1 of {@code CartReadModel} ({@code src/migrations/read-models/CartReadModel/schema-versions.ts}): no
 * {@code checks}. The current version (TypeScript's {@code CartReadModelV2 extends CartReadModel}) is
 * {@code CartReadModel} itself, since records cannot be extended.
 *
 * @param id the cart
 * @param cartItems its lines
 * @param shippingAddress where to ship it
 * @param payment its payment
 * @param cartItemsIds the product ids of its lines
 */
public record CartReadModelV1(UUID id, List<CartItem> cartItems, @Nullable Address shippingAddress,
    @Nullable Payment payment, @Nullable List<String> cartItemsIds) {}
