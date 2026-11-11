package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.commerce;
import static com.boostercloud.integration.harness.Faker.finance;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.lorem;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.entities.ProductType;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/entities.integration.ts}: the reduction of a deleted product
 * and the two chained data migrations started by {@code MigrateCommand} ({@code CartIdDataMigrateV1} sets the
 * quantity of every one-item cart with quantity {@code 1234} to {@code 1234999}, {@code CartIdDataMigrateV2} moves
 * those carts to {@code MigratedCart} entities with the ids {@code NEW_CART_IDS} and quantity {@code 7777999}), which
 * leave at least two rows in {@code DataMigrationsReadModel}.
 *
 * <p>As in TypeScript, {@code client} is one variable: the reducer test switches it to an {@code Admin} client to
 * delete the product and back to the {@code UserWithEmail} client. TypeScript skips {@code Data migration} on AWS; AWS
 * is not ported, so the gate only documents it.
 */
@EndToEnd
@DisplayName("Entities end-to-end tests")
class EntitiesIT {

  private static final String AWS = "AWS";

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;
  private String userToken;

  EntitiesIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    String userEmail = internet.email();
    userToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail");
    client = applicationUnderTest.graphql().client(userToken);
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  @Nested
  @Order(1)
  @DisplayName("Reducers")
  class Reducers {
    private String mockSku;
    private String mockDisplayName;
    private String mockDescription;
    private int mockPriceInCents;
    private String mockCurrency;
    private String mockProductType;
    private Map<String, Object> mockProductDetails;

    private String productId;

    @BeforeEach
    void createProduct() {
      mockSku = random.uuid();
      mockDisplayName = commerce.productName();
      mockDescription = lorem.paragraph();
      mockPriceInCents = random.number(1);
      mockCurrency = finance.currencyCode();
      mockProductType = ProductType.CLOTHING;
      mockProductDetails = variables("color", commerce.color(), "material", commerce.productMaterial());

      // Add one item
      client.mutate("""
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
            $productDetails: JSON
            $productType: JSON
          ) {
            CreateProduct(
              input: {
                sku: $sku
                displayName: $displayName
                description: $description
                priceInCents: $priceInCents
                currency: $currency
                productDetails: $productDetails
                productType: $productType
              }
            )
          }
          """, variables("sku", mockSku, "displayName", mockDisplayName, "description", mockDescription,
          "priceInCents", mockPriceInCents, "currency", mockCurrency, "productDetails", mockProductDetails,
          "productType", mockProductType));

      // Check that new product is available in read model
      GraphQLResponse products = waitForIt(
          () -> client.query("""
              query {
                ProductReadModels {
                  id
                  sku
                  displayName
                  description
                  price {
                    cents
                    currency
                  }
                  availability
                  deleted
                  productDetails
                  productType
                }
              }
              """),
          result -> result != null && result.get("ProductReadModels") instanceof List<?> list
              && list.stream().anyMatch(product -> mockSku.equals(((Map<?, ?>) product).get("sku"))));

      Map<?, ?> product = ((List<?>) products.get("ProductReadModels")).stream()
          .map(item -> (Map<?, ?>) item)
          .filter(item -> mockSku.equals(item.get("sku")))
          .findFirst()
          .orElseThrow();
      productId = (String) product.get("id");

      Map<String, Object> expectedResult = variables(
          "__typename", "ProductReadModel",
          "id", productId,
          "sku", mockSku,
          "displayName", mockDisplayName,
          "description", mockDescription,
          "price", variables("__typename", "Money", "cents", mockPriceInCents, "currency", mockCurrency),
          "availability", 0,
          "deleted", false,
          "productDetails", mockProductDetails,
          "productType", mockProductType);

      assertThat(product).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("should reduce the entity as expected")
    void shouldReduceTheEntityAsExpected() {
      // provision admin user to delete a product
      String adminEmail = internet.email();
      String adminToken = applicationUnderTest.token().forUser(adminEmail, "Admin");
      client = applicationUnderTest.graphql().client(adminToken);

      // Delete a product given an id
      client.mutate("""
          mutation DeleteProduct($productId: ID!) {
            DeleteProduct(input: { productId: $productId })
          }
          """, variables("productId", productId));

      client = applicationUnderTest.graphql().client(userToken);
      // Retrieve updated entity
      GraphQLResponse queryResult = waitForIt(
          () -> client.query("""
              query ProductReadModel($productId: ID!) {
                ProductReadModel(id: $productId) {
                  id
                  sku
                  displayName
                  description
                  price {
                    cents
                    currency
                  }
                  availability
                  deleted
                  productDetails
                  productType
                }
              }
              """, variables("productId", productId)),
          result -> result == null || result.get("ProductReadModel") == null);

      Object productData = queryResult.get("ProductReadModel");

      assertThat(productData).isNull();
      assertThat(queryResult.data()).containsKey("ProductReadModel");
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Data migration")
  class DataMigration {

    @BeforeAll
    void before() {
      if (AWS.equals(AppHelper.testedProvider())) {
        System.out.println("****************** Warning **********************");
        System.out.println("AWS provider does not support entityIds Interface so these tests are skipped for AWS");
        System.out.println("*************************************************");
      }
      assumeFalse(AWS.equals(AppHelper.testedProvider()),
          "AWS provider does not support entityIds Interface so these tests are skipped for AWS");
    }

    @Nested
    @Order(1)
    @DisplayName("with different id and values")
    class WithDifferentIdAndValues {
      private static final int MOCK_CART_COUNT = 3;
      private final List<String> mockCartItems = new ArrayList<>();

      @BeforeEach
      void provisionCarts() {
        List<Callable<GraphQLResponse>> changeCartPromises = new ArrayList<>();

        for (int i = 0; i < MOCK_CART_COUNT; i++) {
          String mockCartId = random.uuid();
          String mockProductId = random.uuid();
          int mockQuantity = Constants.QUANTITY_TO_MIGRATE_DATA;
          mockCartItems.add(mockCartId);

          changeCartPromises.add(() -> client.mutate(CartIT.CHANGE_CART_ITEM,
              variables("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity)));
        }

        Promises.runAll(changeCartPromises);

        List<String> cartIds = List.copyOf(mockCartItems);
        waitForIt(
            () -> client.query(LIST_CART_READ_MODELS, variables("filter", variables("id", variables("in", cartIds)))),
            result -> result != null && result.get("ListCartReadModels.items") instanceof List<?> items
                && items.size() == MOCK_CART_COUNT);
      }

      @Test
      @DisplayName("find migrated entities")
      void findMigratedEntities() {
        client.mutate("""
            mutation MigrateCommand {
              MigrateCommand
            }
            """);

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS,
                variables("filter", variables("id", variables("in", Constants.NEW_CART_IDS)))),
            result -> {
              Object items = result == null ? null : result.get("ListCartReadModels.items");
              String jsonItems = items == null ? "undefined" : BoosterJson.toJson(items);
              if (!(items instanceof List<?> list) || list.size() != MOCK_CART_COUNT) {
                return "Waiting for " + MOCK_CART_COUNT + " items. " + jsonItems + " ";
              }

              if (!allItemsHasQuantity(list, Constants.QUANTITY_AFTER_DATA_MIGRATION_V2)) {
                return "Waiting for quantities to be equal to " + Constants.QUANTITY_AFTER_DATA_MIGRATION_V2 + ". "
                    + jsonItems;
              }

              return true;
            });

        List<?> readModels = (List<?>) queryResult.get("ListCartReadModels.items");
        for (Object readModel : readModels) {
          Map<?, ?> item = (Map<?, ?>) readModel;
          Map<?, ?> cartItem = (Map<?, ?>) ((List<?>) item.get("cartItems")).get(0);
          assertThat(cartItem.get("quantity"))
              .as("Not matching quantity on cartId %s and productId: %s", item.get("id"), cartItem.get("productId"))
              .isEqualTo(Constants.QUANTITY_AFTER_DATA_MIGRATION_V2);
        }

        waitForIt(
            () -> client.query(LIST_DATA_MIGRATIONS_READ_MODELS, variables("filter", Map.of())),
            result -> {
              Object count = result == null ? null : result.get("ListDataMigrationsReadModels.count");
              // TypeScript: `count < 2` (a missing count compares as false and stops the wait as well)
              if (count instanceof Number number && number.doubleValue() < 2) {
                return "Waiting for " + count + " migrations. Done " + count + " migrations";
              }
              return true;
            });
      }
    }

    // Not in TypeScript: the rows behind `count >= 2`. Each migration registers BoosterDataMigrationFinished, so
    // both rows end FINISHED, which the read model stores as `migration.status.toString()` of the numeric enum ("1")
    @Nested
    @Order(2)
    @DisplayName("after the data migrations")
    class AfterTheDataMigrations {

      @Test
      @DisplayName("lists both data migrations as finished")
      void listsBothDataMigrationsAsFinished() {
        GraphQLResponse result = waitForIt(
            () -> client.query(LIST_DATA_MIGRATIONS_READ_MODELS, variables("filter", Map.of())),
            response -> response != null && response.get("ListDataMigrationsReadModels.items") instanceof List<?> items
                && items.size() == 2
                && items.stream().allMatch(item -> "1".equals(((Map<?, ?>) item).get("status"))));

        List<?> items = (List<?>) result.get("ListDataMigrationsReadModels.items");
        assertThat(items.stream().map(item -> (String) ((Map<?, ?>) item).get("id")).toList())
            .containsExactlyInAnyOrder("CartIdDataMigrateV1", "CartIdDataMigrateV2");
        assertThat(items).allSatisfy(item -> {
          Map<?, ?> migration = (Map<?, ?>) item;
          assertThat(migration.get("__typename")).isEqualTo("DataMigrationsReadModel");
          assertThat(migration.get("status")).isEqualTo("1");
          assertThat((String) migration.get("lastUpdated")).isNotEmpty();
        });
        assertThat(result.get("ListDataMigrationsReadModels.count")).isEqualTo(2);
      }
    }
  }

  private static final String LIST_DATA_MIGRATIONS_READ_MODELS = """
      query ListDataMigrationsReadModels($filter: ListDataMigrationsReadModelFilter) {
        ListDataMigrationsReadModels(filter: $filter) {
          items {
            id
            status
            lastUpdated
          }
          count
          cursor
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            cartItems {
              productId
              quantity
            }
          }
        }
      }
      """;

  private static boolean allItemsHasQuantity(List<?> items, int quantity) {
    return items.stream().allMatch(item -> {
      Map<?, ?> cartItem = (Map<?, ?>) ((List<?>) ((Map<?, ?>) item).get("cartItems")).get(0);
      return cartItem.get("quantity") instanceof Number number && number.doubleValue() == quantity;
    });
  }
}
