package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.integration.app.readmodels.CartReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.CategoryReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.types.searcher.SortFor;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The read side through GraphQL: by id, the deprecated plural query, the paginated {@code List*} query with filters,
 * sorting and cursors, selections with calculated fields, before hooks, non-exposed properties, and projections of
 * many entities into one read model under optimistic concurrency.
 */
class ReadModelQueriesIntegrationTest extends IntegrationTestSupport {

  private static final String CREATE_PRODUCT = """
      mutation ($sku: String!, $name: String!, $price: Float!, $category: String!) {
        CreateProduct(input: {sku: $sku, displayName: $name, price: $price, category: $category})
      }""";

  private String createProduct(String sku, String name, double price, String category) {
    Map<String, Object> result = graphQL(CREATE_PRODUCT,
        map("sku", sku, "name", name, "price", price, "category", category), TestTokens.admin());
    return (String) data(result).get("CreateProduct");
  }

  private String apple;
  private String pear;
  private String banana;
  private String hammer;

  @BeforeEach
  void createCatalogue() {
    apple = createProduct("FRU-APPLE", "Apple", 1.5, "fruit");
    pear = createProduct("FRU-PEAR", "Pear", 2.25, "fruit");
    banana = createProduct("FRU-BANANA", "Banana", 0.75, "fruit");
    hammer = createProduct("TOO-HAMMER", "Hammer", 12, "tools");
  }

  @Test
  void findsAReadModelById() {
    Map<String, Object> result = graphQL("query ($id: ID!) { ProductReadModel(id: $id) { id sku displayName price category } }",
        map("id", apple), TestTokens.customer());

    assertThat(data(result)).isEqualTo(Map.of("ProductReadModel",
        map("id", apple, "sku", "FRU-APPLE", "displayName", "Apple", "price", 1.5, "category", "fruit")));
  }

  @Test
  void filtersWithTheDeprecatedPluralQuery() {
    Map<String, Object> result = graphQL("""
        { ProductReadModels(filter: {category: {eq: "fruit"}, price: {gt: 1}}) { sku } }""", TestTokens.customer());

    assertThat(listAt(data(result), "ProductReadModels")).extracting(item -> item.get("sku"))
        .containsExactlyInAnyOrder("FRU-APPLE", "FRU-PEAR");
  }

  @Test
  void combinesFiltersWithAndOrAndNot() {
    Map<String, Object> result = graphQL("""
        { ListProductReadModels(filter: {
            or: [{sku: {beginsWith: "TOO-"}}, {and: [{category: {eq: "fruit"}}, {not: {price: {lt: 1}}}]}]
          }, sortBy: {sku: ASC}) { items { sku } count } }""", TestTokens.customer());

    assertThat(listAt(data(result), "ListProductReadModels", "items")).extracting(item -> item.get("sku"))
        .containsExactly("FRU-APPLE", "FRU-PEAR", "TOO-HAMMER");
  }

  @Test
  void pagesThroughASortedListWithTheCursor() {
    String query = """
        query ($cursor: JSON) {
          ListProductReadModels(filter: {category: {eq: "fruit"}}, sortBy: {price: DESC}, limit: 2, afterCursor: $cursor) {
            items { sku price } count cursor
          }
        }""";

    Map<String, Object> first = mapAt(data(graphQL(query, map("cursor", null), TestTokens.customer())),
        "ListProductReadModels");
    assertThat(first.get("items")).isEqualTo(List.of(map("sku", "FRU-PEAR", "price", 2.25),
        map("sku", "FRU-APPLE", "price", 1.5)));
    assertThat(first.get("count")).isEqualTo(2);
    assertThat(first.get("cursor")).isEqualTo(Map.of("id", "2"));

    Map<String, Object> second = mapAt(data(graphQL(query, map("cursor", first.get("cursor")),
        TestTokens.customer())), "ListProductReadModels");
    assertThat(second.get("items")).isEqualTo(List.of(map("sku", "FRU-BANANA", "price", 0.75)));
    assertThat(second.get("count")).isEqualTo(1);
    assertThat(second.get("cursor")).isEqualTo(Map.of("id", "4"));
  }

  @Test
  void selectsCalculatedFieldsTogetherWithTheirDependencies() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"" + apple + "\", quantity: 3}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"" + pear + "\", quantity: 4}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-2\", productId: \"" + pear + "\", quantity: 1}) }");

    Map<String, Object> result = graphQL("""
        { ListCartReadModels(filter: {id: {in: ["cart-1", "cart-2"]}}, sortBy: {id: DESC}) {
            items { id totalQuantity } count } }""");

    assertThat(listAt(data(result), "ListCartReadModels", "items"))
        .containsExactly(map("id", "cart-2", "totalQuantity", 1.0), map("id", "cart-1", "totalQuantity", 7.0));
  }

  @Test
  void selectsNestedPathsOfListsAndCountsWithoutItems() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"" + apple + "\", quantity: 3}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"" + pear + "\", quantity: 4}) }");

    Map<String, Object> nested = graphQL("{ ListCartReadModels { items { items { productId } } } }");
    Map<String, Object> countOnly = graphQL("{ ListCartReadModels(filter: {checks: {gte: 1}}) { count } }");

    assertThat(data(nested)).isEqualTo(Map.of("ListCartReadModels", Map.of("items", List.of(
        Map.of("items", List.of(Map.of("productId", apple), Map.of("productId", pear)))))));
    assertThat(data(countOnly)).isEqualTo(Map.of("ListCartReadModels", Map.of("count", 1)));
  }

  @Test
  void theSearcherPagesFromCode() {
    var first = Booster.readModel(ProductReadModel.class).sortBy(SortFor.of("price", "ASC")).limit(3)
        .searchPaginated();
    var second = Booster.readModel(ProductReadModel.class).sortBy(SortFor.of("price", "ASC")).limit(3)
        .afterCursor(first.cursor()).searchPaginated();

    assertThat(first.items()).extracting(ProductReadModel::sku).containsExactly("FRU-BANANA", "FRU-APPLE", "FRU-PEAR");
    assertThat(second.items()).extracting(ProductReadModel::sku).containsExactly("TOO-HAMMER");
    assertThat(second.count()).isEqualTo(1);
  }

  @Test
  void filtersOnArraysOfObjects() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"" + apple + "\", quantity: 3}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-2\", productId: \"" + pear + "\", quantity: 1}) }");

    Map<String, Object> result = graphQL("{ CartReadModels(filter: {items: {includes: {productId: \"" + pear
        + "\"}}}) { id } }");

    assertThat(data(result)).isEqualTo(Map.of("CartReadModels", List.of(Map.of("id", "cart-2"))));
  }

  @Test
  void theReadModelBeforeHookCanRejectAndRewriteRequests() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"secret-1\", productId: \"p\", quantity: 1}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-9\", productId: \"p\", quantity: 9}) }");

    Map<String, Object> denied = graphQL("{ CartReadModel(id: \"secret-1\") { id } }", TestTokens.customer());
    assertThat(errorMessage(denied)).isEqualTo("Secret carts are only visible to admins");
    assertThat(errors(denied).getFirst().get("extensions")).isEqualTo(Map.of("code", "NotAuthorizedError"));
    assertThat(data(graphQL("{ CartReadModel(id: \"secret-1\") { id } }", TestTokens.admin())))
        .isEqualTo(Map.of("CartReadModel", Map.of("id", "secret-1")));
    assertThat(data(graphQL("{ CartReadModel(id: \"alias-cart-9\") { id totalQuantity } }")))
        .isEqualTo(Map.of("CartReadModel", Map.of("id", "cart-9", "totalQuantity", 9.0)));
  }

  @Test
  void nonExposedPropertiesAreStoredButNotPartOfTheSchema() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"p\", quantity: 1}) }");

    Map<String, Object> result = graphQL("{ CartReadModel(id: \"cart-1\") { id internalNote } }");
    assertThat(errorMessage(result)).contains("internalNote");
    Map<String, Object> type = mapAt(data(graphQL("{ __type(name: \"CartReadModel\") { fields { name } } }")), "__type");
    assertThat(listAt(type, "fields")).extracting(field -> field.get("name"))
        .containsExactly("id", "items", "checks", "totalQuantity");
    Map<String, Object> input = mapAt(data(graphQL("{ __type(name: \"CartTotalQuantityInput\") { inputFields { name } } }")),
        "__type");
    assertThat(listAt(input, "inputFields")).extracting(field -> field.get("name")).containsExactly("cartId");
    assertThat(provider.readModels().documents("CartReadModel").getFirst()).containsEntry("internalNote", "projected 2");
  }

  @Test
  void manyEntitiesProjectedConcurrentlyIntoOneReadModelRetryOnConflicts() {
    Map<String, Object> result = graphQL("mutation { ImportProducts(input: {category: \"garden\", count: 8}) }",
        TestTokens.admin());
    assertThat(data(result)).isEqualTo(Map.of("ImportProducts", 8.0));

    CategoryReadModel garden = Booster.readModel(CategoryReadModel.class)
        .filter(Map.of("id", Map.of("eq", "garden"))).searchOne().orElseThrow();
    assertThat(garden.productIds()).hasSize(8).doesNotHaveDuplicates();
    assertThat(Booster.readModel(CategoryReadModel.class).filter(Map.of("id", Map.of("eq", "fruit"))).searchOne())
        .hasValueSatisfying(fruit -> assertThat(fruit.productIds())
            .containsExactly(UUID.of(apple), UUID.of(pear), UUID.of(banana)));
    assertThat(provider.readModels().documents("CategoryReadModel"))
        .filteredOn(document -> "garden".equals(document.get("id")))
        .singleElement().satisfies(document -> assertThat(at(document, "boosterMetadata", "version")).isEqualTo(8));
  }

  @Test
  void injectedConflictsAreRetriedWithTheLatestVersion() {
    provider.readModels().failNextStores(3);

    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"p\", quantity: 2}) }");

    assertThat(provider.readModels().concurrencyConflicts()).isGreaterThanOrEqualTo(3);
    assertThat(Booster.readModel(CartReadModel.class).filter(Map.of("id", Map.of("eq", "cart-1"))).searchOne())
        .hasValueSatisfying(cart -> {
          assertThat(cart.totalQuantity()).isEqualTo(2);
          assertThat(cart.checks()).isEqualTo(1);
        });
  }

  @Test
  void aQueryReadsReadModelsWithTheSearcher() {
    Map<String, Object> result = graphQL("{ ProductsInCategory(input: {category: \"fruit\"}) }", TestTokens.admin());

    assertThat(data(result)).isEqualTo(Map.of("ProductsInCategory", List.of("FRU-APPLE", "FRU-BANANA", "FRU-PEAR")));
  }

  @Test
  void aQueryBeforeHookSetsANonExposedField() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-1\", productId: \"p\", quantity: 3}) }");

    assertThat(data(graphQL("{ CartTotalQuantity(input: {cartId: \"cart-1\"}) }")))
        .isEqualTo(Map.of("CartTotalQuantity", 3.0));
    assertThat(data(graphQL("{ CartTotalQuantity(input: {cartId: \"cart-1\"}) }", TestTokens.admin())))
        .isEqualTo(Map.of("CartTotalQuantity", 30.0));
  }

  @Test
  void updatesAndDeletionsReachTheReadModels() {
    graphQL("mutation ($id: ID!) { UpdateProduct(input: {productId: $id, displayName: \"Green apple\", price: 1.75}) }",
        map("id", apple), TestTokens.admin());
    graphQL("mutation ($id: ID!) { DeleteProduct(input: {productId: $id}) }", map("id", hammer), TestTokens.admin());

    assertThat(data(graphQL("query ($id: ID!) { ProductReadModel(id: $id) { displayName price } }", map("id", apple),
        TestTokens.admin()))).isEqualTo(Map.of("ProductReadModel", map("displayName", "Green apple", "price", 1.75)));
    assertThat(data(graphQL("query ($id: ID!) { ProductReadModel(id: $id) { id } }", map("id", hammer),
        TestTokens.admin()))).isEqualTo(map("ProductReadModel", null));
    assertThat(Booster.readModel(CategoryReadModel.class).filter(Map.of("id", Map.of("eq", "tools"))).searchOne())
        .hasValueSatisfying(tools -> assertThat(tools.productIds()).isEmpty());
  }
}
