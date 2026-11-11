package com.boostercloud.integration.migrations.data;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.BoosterDataMigrations;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.decorators.DataMigration;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.entities.MigratedCart;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Second data migration ({@code src/migrations/data/cart-id-data-migrate-v2.ts}): the carts migrated by
 * {@link CartIdDataMigrateV1} (one line of quantity {@value Constants#QUANTITY_AFTER_DATA_MIGRATION_V1}) become
 * {@link MigratedCart}s with the ids of {@link Constants#NEW_CART_IDS}, in order, and quantity
 * {@value Constants#QUANTITY_AFTER_DATA_MIGRATION_V2}. A fourth cart gets a {@code null} id (TypeScript:
 * {@code NEW_CART_IDS[3]} is {@code undefined}). Without matching carts it returns without finishing, like V1.
 */
@DataMigration(order = 2)
public class CartIdDataMigrateV2 {

  /**
   * @param register the register of the migration
   */
  public static void start(Register register) {
    System.out.println("Data migration");
    List<Cart> validCarts = CartIdDataMigrateV1.cartsWithSingleItemOfQuantity(Constants.QUANTITY_AFTER_DATA_MIGRATION_V1);
    if (validCarts.isEmpty()) {
      return;
    }
    // TypeScript increments the index synchronously inside map: the ids follow the order of the carts
    List<Callable<UUID>> migrations = new ArrayList<>();
    for (int index = 0; index < validCarts.size(); index++) {
      Cart validCart = validCarts.get(index);
      UUID newCartId = index < Constants.NEW_CART_IDS.size() ? UUID.of(Constants.NEW_CART_IDS.get(index)) : null;
      migrations.add(() -> {
        List<CartItem> cartItems = CartIdDataMigrateV1.withFirstQuantity(validCart.cartItems(),
            Constants.QUANTITY_AFTER_DATA_MIGRATION_V2);
        MigratedCart newCart =
            new MigratedCart(newCartId, cartItems, validCart.shippingAddress(), validCart.checks());
        BoosterDataMigrations.migrateEntity("Cart", validCart.id(), newCart);
        System.out.println("Migrated " + newCart);
        return newCart.id();
      });
    }
    Promises.runAll(migrations);
    register.events(new BoosterDataMigrationFinished("CartIdDataMigrateV2"));
  }
}
