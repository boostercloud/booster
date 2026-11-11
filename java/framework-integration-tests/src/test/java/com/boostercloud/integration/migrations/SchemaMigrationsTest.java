package com.boostercloud.integration.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.commands.UpdateProduct;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import com.boostercloud.integration.entities.Payment;
import com.boostercloud.integration.entities.Product;
import com.boostercloud.integration.events.ProductUpdateReason;
import com.boostercloud.integration.events.ProductUpdated;
import com.boostercloud.integration.migrations.commands.updateproduct.UpdateProductMigration;
import com.boostercloud.integration.migrations.commands.updateproduct.UpdateProductV1;
import com.boostercloud.integration.migrations.entities.product.ProductMigration;
import com.boostercloud.integration.migrations.entities.product.ProductV1;
import com.boostercloud.integration.migrations.events.productupdated.ProductUpdatedMigration;
import com.boostercloud.integration.migrations.events.productupdated.ProductUpdatedV1;
import com.boostercloud.integration.migrations.events.productupdated.ProductUpdatedV2;
import com.boostercloud.integration.migrations.events.productupdated.ProductUpdatedV3;
import com.boostercloud.integration.migrations.readmodels.cartreadmodel.CartReadModelMigration;
import com.boostercloud.integration.migrations.readmodels.cartreadmodel.CartReadModelV1;
import com.boostercloud.integration.migrations.readmodels.productreadmodel.ProductReadModelMigration;
import com.boostercloud.integration.migrations.readmodels.productreadmodel.ProductReadModelV1;
import com.boostercloud.integration.readmodels.CartReadModel;
import com.boostercloud.integration.readmodels.ProductReadModel;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The schema migrations of {@code src/migrations/**} (the data migrations need a running application). */
class SchemaMigrationsTest {

  private static final UUID ID = UUID.of("p1");
  private static final Money PRICE = new Money(1999, "EUR");
  private static final List<Picture> PICTURES = List.of(new Picture(URI.create("https://example.com/p1.png")));
  private static final com.boostercloud.integration.migrations.events.productupdated.Product OLD_PRODUCT =
      new com.boostercloud.integration.migrations.events.productupdated.Product(ID, "SKU", "Chair", "A chair", PRICE,
          PICTURES, false);

  @Test
  void productUpdatedGetsTheDefaultReasonThenAnEnumThenTheExpandedFields() {
    ProductUpdatedMigration migration = new ProductUpdatedMigration();

    ProductUpdatedV2 v2 = migration.addReasonField(new ProductUpdatedV1(OLD_PRODUCT));
    ProductUpdatedV3 v3 = migration.changeReasonFieldToEnum(v2);
    ProductUpdated v4 = migration.expandProductFields(v3);

    assertThat(v2).isEqualTo(new ProductUpdatedV2(OLD_PRODUCT, "CatalogChange"));
    assertThat(v3).isEqualTo(new ProductUpdatedV3(OLD_PRODUCT, ProductUpdateReason.CatalogChange));
    assertThat(v4).isEqualTo(new ProductUpdated(ID, "SKU", "Chair", "A chair", PRICE, PICTURES, false,
        ProductUpdateReason.CatalogChange, Map.of(), "Other"));
    assertThat(v4.entityID()).isEqualTo(ID);
  }

  @Test
  void theReasonIsLookedUpByExactConstantNameWithTheCatalogChangeFallback() {
    ProductUpdatedMigration migration = new ProductUpdatedMigration();

    assertThat(migration.changeReasonFieldToEnum(new ProductUpdatedV2(OLD_PRODUCT, "FixName")).reason())
        .isEqualTo(ProductUpdateReason.FixName);
    assertThat(migration.changeReasonFieldToEnum(new ProductUpdatedV2(OLD_PRODUCT, "FixDescription")).reason())
        .isEqualTo(ProductUpdateReason.FixDescription);
    for (String reason : java.util.Arrays.asList("fixname", "Other", "", null)) {
      assertThat(migration.changeReasonFieldToEnum(new ProductUpdatedV2(OLD_PRODUCT, reason)).reason())
          .as(String.valueOf(reason)).isEqualTo(ProductUpdateReason.CatalogChange);
    }
  }

  @Test
  void updateProductSplitsTheDescriptionIntoAShortOneAndAnEmptyLongOne() {
    UpdateProduct migrated = new UpdateProductMigration().splitDescriptionFieldIntoShortAndLong(
        new UpdateProductV1(ID, "SKU", "Chair", "A chair", PRICE, PICTURES, true, ProductUpdateReason.FixName));

    assertThat(migrated).isEqualTo(new UpdateProduct(ID, "SKU", "Chair", "A chair", "", PRICE, PICTURES, true,
        ProductUpdateReason.FixName, null, null));
  }

  @Test
  void productRenamesNameToDisplayNameWithTheConstructorDefaults() {
    Product migrated = new ProductMigration().changeNameFieldToDisplayName(
        new ProductV1(ID, "SKU", "Chair", "A chair", PRICE, PICTURES, false));

    assertThat(migrated).isEqualTo(new Product(ID, "SKU", "Chair", "A chair", PRICE, PICTURES, false, 0, Map.of(),
        "Other"));
  }

  @Test
  void readModelsGetTheirNewFieldsAndLoseThePictures() {
    Address address = new Address("f", "l", "spain", "s", "11111", "a");
    Payment payment = new Payment(UUID.of("pay1"), UUID.of("c1"), "token");
    List<CartItem> items = List.of(new CartItem(ID, 1));

    CartReadModel cart = new CartReadModelMigration().splitDescriptionFieldIntoShortAndLong(
        new CartReadModelV1(UUID.of("c1"), items, address, payment, List.of("p1")));
    ProductReadModel product = new ProductReadModelMigration().splitDescriptionFieldIntoShortAndLong(
        new ProductReadModelV1(ID, "SKU", "Chair", "A chair", PRICE, PICTURES, false));

    assertThat(cart).isEqualTo(new CartReadModel(UUID.of("c1"), items, 0, address, payment, List.of("p1")));
    assertThat(product).isEqualTo(new ProductReadModel(ID, "SKU", "Chair", "A chair", 1000, false, Map.of(), "Other",
        PRICE, List.of()));
  }
}
