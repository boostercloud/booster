package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.app.entities.Product;
import com.boostercloud.framework.core.integration.app.migrations.UpgradeProductReadModels;
import com.boostercloud.framework.core.integration.app.readmodels.CategoryReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Schema migrations of data written by an older version of the application (events and read models are upgraded
 * when read) and data migrations ({@code @DataMigration}: ordered, tracked by {@code BoosterDataMigrationEntity},
 * run once, migrating entities with {@code migrateEntity} and read models with {@code migrateAll}).
 */
class MigrationsIntegrationTest extends IntegrationTestSupport {

  private EventEnvelope oldProductCreated(String productId, String sku, String displayName) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(productId))
        .requestID(UUID.of("old-request"))
        .entityTypeName("Product")
        .typeName("ProductCreated")
        .value(map("productId", productId, "sku", sku, "displayName", displayName, "price", 5))
        .id("old-event-" + productId)
        .createdAt("2020-01-01T00:00:00.000Z")
        .build();
  }

  private String createProduct(String sku, String name) {
    return (String) data(graphQL("mutation ($sku: String!, $name: String!) { CreateProduct(input: "
        + "{sku: $sku, displayName: $name, price: 1, category: \"misc\"}) }", map("sku", sku, "name", name),
        TestTokens.admin())).get("CreateProduct");
  }

  @Test
  void theCurrentVersionsComeFromTheMigrations() {
    assertThat(config.currentVersionFor("ProductCreated")).isEqualTo(2);
    assertThat(config.currentVersionFor("ProductReadModel")).isEqualTo(2);
    assertThat(config.currentVersionFor("CartItemChanged")).isEqualTo(1);

    createProduct("NEW", "New");

    assertThat(provider.events().events()).singleElement().satisfies(event -> assertThat(event.version()).isEqualTo(2));
    assertThat(provider.readModels().documents("ProductReadModel")).singleElement()
        .satisfies(document -> assertThat(at(document, "boosterMetadata", "schemaVersion")).isEqualTo(2));
  }

  @Test
  void oldEventsAreMigratedBeforeTheyAreReduced() {
    EventEnvelope old = oldProductCreated("old-1", "OLD-1", "Old product");
    provider.events().plant(old);

    provider.events().dispatch(List.of(provider.events().events().getFirst()));

    assertThat(Booster.entity(Product.class, UUID.of("old-1")))
        .contains(new Product(UUID.of("old-1"), "OLD-1", "Old product", 5, "uncategorized", false));
    assertThat(data(graphQL("{ ProductReadModel(id: \"old-1\") { sku category } }", TestTokens.admin())))
        .isEqualTo(Map.of("ProductReadModel", map("sku", "OLD-1", "category", "uncategorized")));
    assertThat(Booster.readModel(CategoryReadModel.class).filter(Map.of("id", Map.of("eq", "uncategorized")))
        .searchOne()).hasValueSatisfying(category -> assertThat(category.productIds()).containsExactly(UUID.of("old-1")));
    assertThat(provider.events().events()).singleElement()
        .satisfies(event -> assertThat(event.version()).isEqualTo(1));
    assertThat(AppState.EVENTS).containsExactly("ProductCreated");
  }

  @Test
  void anEventDispatchedTwiceIsProcessedOnce() {
    provider.events().plant(oldProductCreated("old-1", "OLD-1", "Old product"));
    EventEnvelope stored = provider.events().events().getFirst();

    provider.events().dispatch(List.of(stored));
    provider.events().dispatch(List.of(stored));

    assertThat(AppState.EVENTS).containsExactly("ProductCreated");
    assertThat(provider.events().snapshotsStored()).isEqualTo(1);
  }

  @Test
  void oldReadModelsAreMigratedWhenRead() {
    provider.readModels().plant("ProductReadModel", map("id", "legacy-1", "sku", "LEG-1", "displayName", "Legacy",
        "price", 9, "boosterMetadata", map("version", 3, "schemaVersion", 1)));

    assertThat(data(graphQL("{ ProductReadModel(id: \"legacy-1\") { sku category } }", TestTokens.customer())))
        .isEqualTo(Map.of("ProductReadModel", map("sku", "LEG-1", "category", "legacy")));
    assertThat(Booster.readModel(ProductReadModel.class).search()).containsExactly(
        new ProductReadModel(UUID.of("legacy-1"), "LEG-1", "Legacy", 9, "legacy"));
    // As in TypeScript, searches with a selection (every GraphQL list query) return the stored properties without
    // migrating them: a property added by a migration is missing until the read model is stored again
    assertThat(data(graphQL("{ ListProductReadModels(filter: {sku: {eq: \"LEG-1\"}}) { items { id sku } } }",
        TestTokens.customer()))).isEqualTo(Map.of("ListProductReadModels",
            Map.of("items", List.of(map("id", "legacy-1", "sku", "LEG-1")))));
    assertThat(errorMessage(graphQL("{ ListProductReadModels { items { category } } }", TestTokens.customer())))
        .contains("'/ListProductReadModels/items[0]/category' was declared as a non null type");
    assertThat(provider.readModels().documents("ProductReadModel")).singleElement()
        .satisfies(document -> assertThat(document).doesNotContainKey("category"));
  }

  @Test
  void dataMigrationsRunOnceInOrder() {
    String apple = createProduct("APL", "  apple ");
    String pear = createProduct("PER", "pear");
    String plum = createProduct("PLM", "plum");
    provider.readModels().plant("ProductReadModel", map("id", "legacy-1", "sku", "LEG-1", "displayName", "Legacy",
        "price", 9, "boosterMetadata", map("version", 3, "schemaVersion", 1)));

    Map<String, Object> first = graphQL("mutation { RunDataMigrations }", TestTokens.admin());

    assertThat(data(first)).isEqualTo(Map.of("RunDataMigrations", true));
    assertThat(AppState.MIGRATIONS).containsExactly("NormalizeProductNames", "UpgradeProductReadModels");
    for (String id : List.of(apple, pear, plum)) {
      Product product = Booster.entity(Product.class, UUID.of(id)).orElseThrow();
      assertThat(product.displayName()).isEqualTo(product.displayName().trim().toUpperCase());
    }
    assertThat(data(graphQL("query ($id: ID!) { ProductReadModel(id: $id) { displayName } }", map("id", apple),
        TestTokens.admin()))).isEqualTo(Map.of("ProductReadModel", Map.of("displayName", "APPLE")));
    assertThat(provider.events().events()).filteredOn(event -> event.typeName().equals("BoosterEntityMigrated"))
        .hasSize(3).allSatisfy(event -> {
          assertThat(event.entityTypeName()).isEqualTo("Product");
          assertThat(event.superKind()).isEqualTo(SuperKindType.BOOSTER);
        });
    assertThat(UpgradeProductReadModels.migrated).isEqualTo(1);
    assertThat(provider.readModels().documents("ProductReadModel"))
        .filteredOn(document -> "legacy-1".equals(document.get("id"))).singleElement().satisfies(document -> {
          assertThat(document).containsEntry("category", "legacy");
          assertThat(at(document, "boosterMetadata", "schemaVersion")).isEqualTo(2);
          assertThat(at(document, "boosterMetadata", "version")).isEqualTo(4);
        });
    List<String> statuses = List.of("NormalizeProductNames", "UpgradeProductReadModels").stream()
        .map(name -> provider.events().latestEntitySnapshot(config, "BoosterDataMigrationEntity", UUID.of(name))
            .orElseThrow().value())
        .map(value -> String.valueOf(((Map<?, ?>) value).get("status")))
        .toList();
    assertThat(statuses).containsExactly("1", "1");

    Map<String, Object> second = graphQL("mutation { RunDataMigrations }", TestTokens.admin());

    assertThat(data(second)).isEqualTo(Map.of("RunDataMigrations", false));
    assertThat(AppState.MIGRATIONS).hasSize(2);
  }

  @Test
  void theDataMigrationEventsAreNotReadableThroughTheApi() {
    graphQL("mutation { RunDataMigrations }", TestTokens.admin());

    assertThat(errorMessage(graphQL("{ eventsByEntity(entity: BoosterDataMigrationEntity) { type } }",
        TestTokens.admin()))).isEqualTo("Access denied for this resource");
    assertThat(provider.events().events()).filteredOn(event -> event.entityTypeName().equals("BoosterDataMigrationEntity"))
        .extracting(EventEnvelope::typeName)
        .containsExactly("BoosterDataMigrationStarted", "BoosterDataMigrationFinished", "BoosterDataMigrationStarted",
            "BoosterDataMigrationFinished");
    assertThat(provider.events().events()).allSatisfy(
        event -> assertThat(Dates.parse(event.createdAt())).isBeforeOrEqualTo(Dates.now()));
  }
}
