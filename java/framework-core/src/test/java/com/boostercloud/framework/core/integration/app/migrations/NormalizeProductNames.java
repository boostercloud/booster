package com.boostercloud.framework.core.integration.app.migrations;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterDataMigrations;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.decorators.DataMigration;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.app.entities.Product;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import java.util.Locale;
import java.util.Map;

/** Trims and upper-cases every product name, two products per page. */
@DataMigration(order = 1)
public class NormalizeProductNames {
  /**
   * @param register the register of the migration
   */
  public static void start(Register register) {
    AppState.MIGRATIONS.add("NormalizeProductNames");
    Map<String, String> cursor = null;
    while (true) {
      PaginatedEntitiesIdsResult page = Booster.entitiesIDs("Product", 2, cursor);
      if (page.items().isEmpty()) {
        break;
      }
      for (PaginatedEntityIdResult item : page.items()) {
        Booster.entity(Product.class, item.entityID()).ifPresent(product -> BoosterDataMigrations.migrateEntity(
            "Product", item.entityID(), product.withDisplayName(product.displayName().trim().toUpperCase(Locale.ROOT))));
      }
      cursor = page.cursor();
    }
    register.events(new BoosterDataMigrationFinished("NormalizeProductNames"));
  }
}
