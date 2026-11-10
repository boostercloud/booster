package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/delete.events.integration.ts}: {@code Booster.deleteEvent}
 * through the {@code HardDelete} command. Deleting the only event of a product blanks the stored event (its value is
 * erased and it gets a {@code deletedAt} stamp), removes the product's snapshot and every read model projected from
 * it, and leaves the events, entities and read models of the other product and of the cart untouched.
 *
 * <p>The tests of {@code When there are events to delete} are sequential steps sharing state, as in mocha (one
 * instance per class, {@code @Order}). TypeScript skips the whole suite on AWS; AWS is not ported, so the gate only
 * documents it.
 */
@EndToEnd
@DisplayName("Remove Events end-to-end tests")
@SuppressWarnings("unchecked") // the responses are raw JSON trees (maps keyed by strings)
class DeleteEventsIT {

  private static final String AWS = "AWS";

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient anonymousClient;
  private GraphQLClient loggedClient;
  private GraphQLClient adminTokenClient;
  private GraphQLClient specialTokenClient;
  private final String firstCartId = random.uuid();
  private final String firstProductId = random.uuid();
  private final String firstProductSku = random.uuid();
  private final String secondProductId = random.uuid();
  private final String secondProductSku = random.uuid();

  DeleteEventsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    if (AWS.equals(AppHelper.testedProvider())) {
      System.out.println("****************** Warning **********************");
      System.out.println("AWS provider does not support remove events so these tests are skipped for AWS");
      System.out.println("*************************************************");
    }
    assumeFalse(AWS.equals(AppHelper.testedProvider()),
        "AWS provider does not support remove events so these tests are skipped for AWS");

    anonymousClient = applicationUnderTest.graphql().client();
    String userEmail = internet.email();
    String userToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail");
    loggedClient = applicationUnderTest.graphql().client(userToken);
    String adminToken = applicationUnderTest.token().forUser("admin@example.com", "Admin");
    adminTokenClient = applicationUnderTest.graphql().client(adminToken);
    String tokenWithSpecialAccess = applicationUnderTest.token().forUser(userEmail, null,
        TokenOptions.builder().customClaim("specialReportAccess", "true").build());
    specialTokenClient = applicationUnderTest.graphql().client(tokenWithSpecialAccess);
  }

  @Test
  @Order(1)
  @DisplayName("When there are not events to delete")
  void whenThereAreNotEventsToDelete() {
    boolean deleted = deleteEvent(anonymousClient, "1", "Product", "");
    assertThat(deleted).isFalse();
  }

  @Nested
  @Order(2)
  @DisplayName("When there are events to delete")
  class WhenThereAreEventsToDelete {
    private Map<String, Object> firstCartReadModel;
    private Map<String, Object> firstProduct;
    private Map<String, Object> firstProductBySkuReadModel;
    private Map<String, Object> firstProductUpdatesReadModel;
    private Map<String, Object> firstSpecialReportsReadModel;
    private Map<String, Object> secondProduct;
    private Map<String, Object> secondProductBySkuReadModel;
    private Map<String, Object> secondProductUpdatesReadModel;
    private Map<String, Object> secondSpecialReportsReadModel;
    private List<Map<String, Object>> events;
    private List<Map<String, Object>> productEvent;
    private Map<String, Object> firstProductEvent;
    private Map<String, Object> secondProductEvent;
    private Map<String, Object> firstCartEvent;

    @Test
    @Order(1)
    @DisplayName("Create a cart")
    void createACart() {
      createCart(anonymousClient, firstCartId, firstProductId);
      firstCartReadModel = getCart(anonymousClient, firstCartId);
      assertThat(firstCartReadModel.get("id")).isEqualTo(firstCartId);
    }

    @Test
    @Order(2)
    @DisplayName("and create a first product")
    void andCreateAFirstProduct() {
      createProduct(loggedClient, firstProductId, firstProductSku);
    }

    @Test
    @Order(3)
    @DisplayName("then the related ReadModels are created")
    void thenTheRelatedReadModelsAreCreated() {
      firstProduct = getProduct(loggedClient, firstProductId);
      assertThat(firstProduct.get("id")).isEqualTo(firstProductId);
      Map<String, Object> firstProductBySkuReadModel = getProductBySku(loggedClient, firstProductSku);
      assertThat(firstProductBySkuReadModel.get("id")).isEqualTo(firstProductSku);
      Map<String, Object> firstProductUpdatesReadModel = getProductUpdates(adminTokenClient, firstProductId);
      assertThat(firstProductUpdatesReadModel.get("id")).isEqualTo(firstProductId);
      Map<String, Object> firstSpecialReportsReadModel = getSpecialReportsReadModel(specialTokenClient, firstProductId);
      assertThat(firstSpecialReportsReadModel.get("id")).isEqualTo(firstProductId);
    }

    @Test
    @Order(4)
    @DisplayName("when a second product is created")
    void whenASecondProductIsCreated() {
      createProduct(loggedClient, secondProductId, secondProductSku);
    }

    @Test
    @Order(5)
    @DisplayName("then the related ReadModels for the second product are created")
    void thenTheRelatedReadModelsForTheSecondProductAreCreated() {
      secondProduct = getProduct(loggedClient, secondProductId);
      assertThat(secondProduct.get("id")).isEqualTo(secondProductId);
      Map<String, Object> secondProductBySkuReadModel = getProductBySku(loggedClient, secondProductSku);
      assertThat(secondProductBySkuReadModel.get("id")).isEqualTo(secondProductSku);
      Map<String, Object> secondProductUpdatesReadModel = getProductUpdates(adminTokenClient, secondProductId);
      assertThat(secondProductUpdatesReadModel.get("id")).isEqualTo(secondProductId);
      Map<String, Object> secondSpecialReportsReadModel =
          getSpecialReportsReadModel(specialTokenClient, secondProductId);
      assertThat(secondSpecialReportsReadModel.get("id")).isEqualTo(secondProductId);
    }

    @Test
    @Order(6)
    @DisplayName("when find the event for the first product")
    void whenFindTheEventForTheFirstProduct() {
      events = queryProductByEntity(loggedClient, firstProductId);
      productEvent = events.stream().filter(event -> firstProductId.equals(event.get("entityID"))).toList();
      firstProductEvent = productEvent.get(0);
    }

    @Test
    @Order(7)
    @DisplayName("and delete it")
    void andDeleteIt() {
      boolean deleted = deleteEvent(anonymousClient, firstProductEvent.get("entityID").toString(),
          (String) firstProductEvent.get("entity"), (String) firstProductEvent.get("createdAt"));
      assertThat(deleted).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("when find the event for the first product after delete")
    void whenFindTheEventForTheFirstProductAfterDelete() {
      events = queryProductByEntity(loggedClient, firstProductId);
      productEvent = events.stream().filter(event -> firstProductId.equals(event.get("entityID"))).toList();
      firstProductEvent = productEvent.get(0);
    }

    @Test
    @Order(9)
    @DisplayName("then the first event is set as deleted")
    void thenTheFirstEventIsSetAsDeleted() {
      assertThat(firstProductEvent).containsKey("deletedAt");
      // TypeScript's `not.to.be.undefined` also accepts null; the port requires the deletion stamp
      assertThat(firstProductEvent.get("deletedAt")).isNotNull();
      assertThat((Map<String, Object>) firstProductEvent.get("value")).doesNotContainKey("productId");
    }

    @Test
    @Order(10)
    @DisplayName("and there are not ReadModels for first product id")
    void andThereAreNotReadModelsForFirstProductId() {
      firstProduct = getProductUndefined(loggedClient, firstProductId);
      assertThat(firstProduct).isNull();
      firstProductBySkuReadModel = getProductBySkuUndefined(loggedClient, firstProductSku);
      assertThat(firstProductBySkuReadModel).isNull();
      firstProductUpdatesReadModel = getProductUpdatesUndefined(adminTokenClient, firstProductId);
      assertThat(firstProductUpdatesReadModel).isNull();
      firstSpecialReportsReadModel = getSpecialReportsReadModelUndefined(specialTokenClient, firstProductId);
      assertThat(firstSpecialReportsReadModel).isNull();
    }

    @Test
    @Order(11)
    @DisplayName("when find the event for the second product")
    void whenFindTheEventForTheSecondProduct() {
      events = queryProductByEntity(loggedClient, secondProductId);
      productEvent = events.stream().filter(event -> secondProductId.equals(event.get("entityID"))).toList();
      secondProductEvent = productEvent.get(0);
    }

    @Test
    @Order(12)
    @DisplayName("then the second event is not changed")
    void thenTheSecondEventIsNotChanged() {
      assertThat(secondProductEvent).containsEntry("deletedAt", null);
      assertThat(((Map<String, Object>) secondProductEvent.get("value")).get("productId")).isEqualTo(secondProductId);
    }

    @Test
    @Order(13)
    @DisplayName("and ReadModels for second product id are not deleted")
    void andReadModelsForSecondProductIdAreNotDeleted() {
      secondProduct = getProduct(loggedClient, secondProductId);
      assertThat(secondProduct.get("id")).isEqualTo(secondProductId);
      secondProductBySkuReadModel = getProductBySku(loggedClient, secondProductSku);
      assertThat(secondProductBySkuReadModel.get("id")).isEqualTo(secondProductSku);
      secondProductUpdatesReadModel = getProductUpdates(adminTokenClient, secondProductId);
      assertThat(secondProductUpdatesReadModel.get("id")).isEqualTo(secondProductId);
      secondSpecialReportsReadModel = getSpecialReportsReadModel(specialTokenClient, secondProductId);
      assertThat(secondSpecialReportsReadModel.get("id")).isEqualTo(secondProductId);
    }

    @Test
    @Order(14)
    @DisplayName("and there are not entities for first product but for product 2")
    void andThereAreNotEntitiesForFirstProductButForProduct2() {
      Map<String, Object> productsEntities = findEntities(loggedClient, "Product");
      List<Map<String, Object>> items = (List<Map<String, Object>>) productsEntities.get("items");
      assertThat(items.stream().anyMatch(product -> firstProductId.equals(product.get("entityID")))).isFalse();
      assertThat(items.stream().anyMatch(product -> secondProductId.equals(product.get("entityID")))).isTrue();
    }

    @Test
    @Order(15)
    @DisplayName("when find the event for the cart")
    void whenFindTheEventForTheCart() {
      events = queryCartByEntity(loggedClient, firstCartId);
      List<Map<String, Object>> cartEvent =
          events.stream().filter(event -> firstCartId.equals(event.get("entityID"))).toList();
      firstCartEvent = cartEvent.get(0);
    }

    @Test
    @Order(16)
    @DisplayName("then the cart event is not changed")
    void thenTheCartEventIsNotChanged() {
      assertThat(firstCartEvent).containsEntry("deletedAt", null);
      assertThat(((Map<String, Object>) firstCartEvent.get("value")).get("cartId")).isEqualTo(firstCartId);
    }

    @Test
    @Order(17)
    @DisplayName("and ReadModels for the cart id are not deleted")
    void andReadModelsForTheCartIdAreNotDeleted() {
      firstCartReadModel = getCart(loggedClient, firstCartId);
      assertThat(firstCartReadModel.get("id")).isEqualTo(firstCartId);
    }

    // Not in TypeScript: the providers' delete adapters store the event with `value: {}`, and the event search
    // turns every value into an instance of its event class (`createInstance(ProductCreated, {})`), so only the
    // constructor defaults of ProductCreated are left; their filter (`deletedAt` must not exist) makes a deleted event
    // not deletable any more
    @Test
    @Order(18)
    @DisplayName("and the deleted event only keeps the defaults of its class and can not be deleted again")
    void andTheDeletedEventOnlyKeepsTheDefaultsOfItsClassAndCanNotBeDeletedAgain() {
      assertThat(firstProductEvent.get("type")).isEqualTo("ProductCreated");
      assertThat(firstProductEvent.get("value")).isEqualTo(Map.of("productDetails", Map.of(), "productType", "Other"));

      boolean deleted = deleteEvent(anonymousClient, firstProductEvent.get("entityID").toString(),
          (String) firstProductEvent.get("entity"), (String) firstProductEvent.get("createdAt"));

      assertThat(deleted).isFalse();
    }
  }

  private static void createCart(GraphQLClient client, String cartId, String productId) {
    client.mutate("""
        mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
          ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 1 })
        }
        """, Map.of("cartId", cartId, "productId", productId));
  }

  private static void createProduct(GraphQLClient client, String productId, String sku) {
    client.mutate("""
        mutation CreateProduct($productId: ID!, $sku: String!) {
          CreateProduct(
            input: {
              productID: $productId
              sku: $sku
              displayName: "any"
              description: "any"
              currency: "any"
              priceInCents: 1
            }
          )
        }
        """, Map.of("productId", productId, "sku", sku));
  }

  private static Map<String, Object> getCart(GraphQLClient client, String id) {
    GraphQLResponse result = waitForIt(
        () -> client.query("""
            query CartReadModel($id: ID!) {
              CartReadModel(id: $id) {
                id
              }
            }
            """, Map.of("id", id)),
        response -> id.equals(response == null ? null : response.get("CartReadModel.id")));
    return (Map<String, Object>) result.get("CartReadModel");
  }

  private static GraphQLResponse waitForProduct(GraphQLClient client, String id,
      Function<GraphQLResponse, Object> checkFunction) {
    return waitForIt(
        () -> client.query("""
            query ProductReadModel($id: ID!) {
              ProductReadModel(id: $id) {
                id
              }
            }
            """, Map.of("id", id)),
        checkFunction);
  }

  private static Map<String, Object> getProduct(GraphQLClient client, String id) {
    GraphQLResponse product = waitForProduct(client, id,
        result -> id.equals(result == null ? null : result.get("ProductReadModel.id")));
    return (Map<String, Object>) product.get("ProductReadModel");
  }

  private static Map<String, Object> getProductUndefined(GraphQLClient client, String id) {
    GraphQLResponse product = waitForProduct(client, id, result -> isNullField(result, "ProductReadModel"));
    return (Map<String, Object>) product.get("ProductReadModel");
  }

  private static GraphQLResponse awaitForProductUpdates(GraphQLClient client, String id,
      Function<GraphQLResponse, Object> checkResult) {
    return waitForIt(
        () -> client.query("""
            query ProductUpdatesReadModel($id: ID!) {
              ProductUpdatesReadModel(id: $id) {
                id
              }
            }
            """, Map.of("id", id)),
        checkResult);
  }

  private static Map<String, Object> getProductUpdates(GraphQLClient client, String id) {
    GraphQLResponse result = awaitForProductUpdates(client, id,
        response -> id.equals(response == null ? null : response.get("ProductUpdatesReadModel.id")));
    return (Map<String, Object>) result.get("ProductUpdatesReadModel");
  }

  private static Map<String, Object> getProductUpdatesUndefined(GraphQLClient client, String id) {
    GraphQLResponse result = awaitForProductUpdates(client, id,
        response -> isNullField(response, "ProductUpdatesReadModel"));
    return (Map<String, Object>) result.get("ProductUpdatesReadModel");
  }

  private static GraphQLResponse waitForProductBySku(GraphQLClient client, String id,
      Function<GraphQLResponse, Object> checkResult) {
    return waitForIt(
        () -> client.query("""
            query ProductsBySKU($id: ID!) {
              ProductsBySKU(id: $id) {
                id
              }
            }
            """, Map.of("id", id)),
        checkResult);
  }

  private static Map<String, Object> getProductBySku(GraphQLClient client, String id) {
    GraphQLResponse result = waitForProductBySku(client, id,
        response -> id.equals(response == null ? null : response.get("ProductsBySKU.id")));
    return (Map<String, Object>) result.get("ProductsBySKU");
  }

  private static Map<String, Object> getProductBySkuUndefined(GraphQLClient client, String id) {
    GraphQLResponse result = waitForProductBySku(client, id, response -> isNullField(response, "ProductsBySKU"));
    return (Map<String, Object>) result.get("ProductsBySKU");
  }

  private static GraphQLResponse waitForSpecialReports(GraphQLClient client, String id,
      Function<GraphQLResponse, Object> checkResult) {
    return waitForIt(
        () -> client.query("""
            query SpecialReportsReadModel($id: ID!) {
              SpecialReportsReadModel(id: $id) {
                id
              }
            }
            """, Map.of("id", id)),
        checkResult);
  }

  private static Map<String, Object> getSpecialReportsReadModel(GraphQLClient client, String id) {
    GraphQLResponse result = waitForSpecialReports(client, id,
        response -> id.equals(response == null ? null : response.get("SpecialReportsReadModel.id")));
    return (Map<String, Object>) result.get("SpecialReportsReadModel");
  }

  private static Map<String, Object> getSpecialReportsReadModelUndefined(GraphQLClient client, String id) {
    GraphQLResponse result = waitForSpecialReports(client, id,
        response -> isNullField(response, "SpecialReportsReadModel"));
    return (Map<String, Object>) result.get("SpecialReportsReadModel");
  }

  /** {@code result?.data?.<field> === null}: the field is present in {@code data} with a {@code null} value. */
  private static boolean isNullField(GraphQLResponse result, String field) {
    return result != null && result.data() != null && result.data().containsKey(field)
        && result.data().get(field) == null;
  }

  private static boolean deleteEvent(GraphQLClient client, String entityId, String entityTypeName, String createdAt) {
    GraphQLResponse result = client.mutate("""
        mutation HardDelete($entityId: String!, $entityTypeName: String!, $createdAt: String!) {
          HardDelete(input: { entityId: $entityId, entityTypeName: $entityTypeName, createdAt: $createdAt })
        }
        """, Map.of("entityId", entityId, "entityTypeName", entityTypeName, "createdAt", createdAt));
    return (Boolean) result.get("HardDelete");
  }

  private static GraphQLResponse queryByEntity(GraphQLClient client, String entity, Map<String, String> timeFilters,
      String entityID, Integer limit) {
    String queryTimeFilters =
        timeFilters != null ? ", from:\"" + timeFilters.get("from") + "\" to:\"" + timeFilters.get("to") + "\"" : "";
    String queryEntityID = entityID != null && !entityID.isEmpty() ? ", entityID:\"" + entityID + "\"" : "";
    String queryLimit = limit != null && limit != 0 ? ", limit:" + limit : "";
    return client.query("""
          query {
            eventsByEntity(entity: %s%s%s%s) {
                createdAt
                entity
                entityID
                requestID
                type
                user {
                    id
                    roles
                    username
                }
                value
                deletedAt
            }
          }
        """.formatted(entity, queryEntityID, queryTimeFilters, queryLimit));
  }

  private static List<Map<String, Object>> queryProductByEntity(GraphQLClient client, String entityID) {
    return queryEventsOf(client, "Product", entityID);
  }

  private static List<Map<String, Object>> queryCartByEntity(GraphQLClient client, String entityID) {
    return queryEventsOf(client, "Cart", entityID);
  }

  private static List<Map<String, Object>> queryEventsOf(GraphQLClient client, String entity, String entityID) {
    GraphQLResponse result = waitForIt(
        () -> queryByEntity(client, entity, null, entityID, null),
        response -> response.get("eventsByEntity") instanceof List<?> events
            && events.stream().anyMatch(event -> entityID.equals(((Map<String, Object>) event).get("entityID"))));
    return ((List<?>) result.get("eventsByEntity")).stream()
        .<Map<String, Object>>map(event -> (Map<String, Object>) event)
        .toList();
  }

  private static Map<String, Object> findEntities(GraphQLClient client, String entityName) {
    GraphQLResponse result = client.mutate("""
        mutation EntitiesIdsFinder($entityName: String!, $limit: Float!) {
          EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit })
        }
        """, Map.of("entityName", entityName, "limit", 99999));
    return (Map<String, Object>) result.get("EntitiesIdsFinder");
  }
}
