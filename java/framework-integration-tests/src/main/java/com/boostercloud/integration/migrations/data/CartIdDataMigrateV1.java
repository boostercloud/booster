package com.boostercloud.integration.migrations.data;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterDataMigrations;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.decorators.DataMigration;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.entities.Cart;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * First data migration ({@code src/migrations/data/cart-id-data-migrate-v1.ts}): the carts (first page of 500) with a
 * single line of quantity {@value Constants#QUANTITY_TO_MIGRATE_DATA} get quantity
 * {@value Constants#QUANTITY_AFTER_DATA_MIGRATION_V1}.
 *
 * <p>As in TypeScript, when no cart matches it returns without registering {@code BoosterDataMigrationFinished}, so
 * the migration stays running and is never started again.
 */
@DataMigration(order = 1)
public class CartIdDataMigrateV1 {

  /**
   * @param register the register of the migration
   */
  public static void start(Register register) {
    System.out.println("Data migration");
    List<Cart> validCarts = cartsWithSingleItemOfQuantity(Constants.QUANTITY_TO_MIGRATE_DATA);
    if (validCarts.isEmpty()) {
      return;
    }
    Promises.runAll(validCarts.stream()
        .map(validCart -> (Callable<UUID>) () -> {
          List<CartItem> cartItems = withFirstQuantity(validCart.cartItems(),
              Constants.QUANTITY_AFTER_DATA_MIGRATION_V1);
          Cart newCart = new Cart(validCart.id(), cartItems, validCart.shippingAddress(), validCart.checks());
          BoosterDataMigrations.migrateEntity("Cart", validCart.id(), newCart);
          System.out.println("Migrated " + newCart);
          return validCart.id();
        })
        .toList());
    register.events(new BoosterDataMigrationFinished("CartIdDataMigrateV1"));
  }

  /**
   * The carts of the first page of 500 ids that have exactly one line, of the given quantity.
   *
   * @param quantity the quantity
   * @return the carts, in id-page order
   */
  static List<Cart> cartsWithSingleItemOfQuantity(double quantity) {
    PaginatedEntitiesIdsResult entitiesIdsResult = Booster.entitiesIDs("Cart", 500, null);
    List<Cart> carts = Promises.runAll(entitiesIdsResult.items().stream()
        .map(entity -> (Callable<Cart>) () -> Booster.entity(Cart.class, entity.entityID()).orElse(null))
        .toList());
    return carts.stream()
        .filter(cart -> cart != null && cart.cartItems() != null && cart.cartItems().size() == 1
            && cart.cartItems().getFirst().quantity() == quantity)
        .toList();
  }

  /**
   * {@code cart.cartItems[0].quantity = quantity} on a copy of the lines (they are records).
   *
   * @param cartItems the lines
   * @param quantity the new quantity of the first line
   * @return the new lines
   */
  static List<CartItem> withFirstQuantity(List<CartItem> cartItems, double quantity) {
    List<CartItem> result = new ArrayList<>(cartItems);
    result.set(0, new CartItem(result.getFirst().productId(), quantity));
    return result;
  }
}
