package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.functionality.StoredDocuments.assertDeepContains;
import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.functionality.StoredDocuments.expectedEventDocument;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static com.boostercloud.integration.harness.Faker.commerce;
import static com.boostercloud.integration.harness.Faker.finance;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.lorem;
import static com.boostercloud.integration.harness.Faker.random;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/event-handlers.integration.ts}: moving stock from the
 * provider registers {@code StockMoved}, whose event handler ({@code HandleAvailability}) registers
 * {@code ProductAvailabilityChanged}; both stored events carry the Admin user of the command, with the decoded token
 * claims and header. The event documents are read from the provider's event store; on the local provider, which does
 * not store the cloud partition key {@code entityTypeName_entityID_kind}, the expected documents leave it out (see
 * {@link StoredDocuments}).
 */
@EndToEnd
@DisplayName("Event handlers")
class EventHandlersFunctionalityIT {

  private static final String MOVE_STOCK = """
      mutation MoveStock($productID: String!, $origin: String!, $destination: String!, $quantity: Float!) {
        MoveStock(input: { productID: $productID, origin: $origin, destination: $destination, quantity: $quantity })
      }
      """;

  private static final String CREATE_PRODUCT = """
      mutation CreateProduct(
        $productID: ID!
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
      """;

  /** A few of faker's {@code address.city()} values: the suite only needs a city-like string. */
  private static final List<String> CITIES = List.of("Madrid", "Lake Kaitlyn", "New Elsiebury", "Port Rosalind",
      "South Jaylen", "East Margarett", "West Delphine", "North Emmet", "Kirlinfurt", "Gleasonview");

  private final ApplicationUnderTest applicationUnderTest;
  private String adminEmail;
  private GraphQLClient client;

  EventHandlersFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    adminEmail = internet.email();
    String authToken = applicationUnderTest.token().forUser(adminEmail, "Admin");
    client = applicationUnderTest.graphql().client(authToken);
  }

  @Nested
  @DisplayName("move product")
  class MoveProduct {

    @Test
    @DisplayName("should trigger a new ProductAvailabilityChanged event")
    void shouldTriggerANewProductAvailabilityChangedEvent() {
      String mockProductId = random.uuid();
      String mockOrigin = "provider";
      String mockDestination = city();
      int mockQuantity = random.number(1);

      createProductAndWaitForIt(mockProductId);

      client.mutate(MOVE_STOCK, Map.of("productID", mockProductId, "origin", mockOrigin,
          "destination", mockDestination, "quantity", mockQuantity));

      List<Map<String, Object>> stockEvents = waitForIt(
          () -> applicationUnderTest.query().events("Stock-" + mockProductId + "-event"),
          events -> events != null && events.size() == 1);
      List<Map<String, Object>> productEvents = waitForIt(
          () -> applicationUnderTest.query().events("Product-" + mockProductId + "-event"),
          events -> events != null && events.size() == 2);

      Map<String, Object> stockMovedEvent = stockEvents.get(0);

      Map<String, Object> expectedStockMovedEvent = expectedEventDocument("Stock-" + mockProductId + "-event",
          object(
              "version", 1,
              "value", object(
                  "destination", mockDestination,
                  "quantity", mockQuantity,
                  "productID", mockProductId,
                  "origin", mockOrigin),
              "kind", "event",
              "superKind", "domain",
              "entityTypeName", "Stock",
              "typeName", "StockMoved",
              "entityID", mockProductId,
              "currentUser", expectedCurrentUser(at(stockMovedEvent, "currentUser.claims.iat"))));

      assertDeepContains(stockMovedEvent, expectedStockMovedEvent);

      Map<String, Object> productAvailabilityChangedEvent = productEvents.get(0);

      Map<String, Object> expectedProductAvailabilityChangedEvent = expectedEventDocument(
          "Product-" + mockProductId + "-event",
          object(
              "version", 1,
              "value", object(
                  "productID", mockProductId,
                  "quantity", mockQuantity),
              "kind", "event",
              "superKind", "domain",
              "entityTypeName", "Product",
              "typeName", "ProductAvailabilityChanged",
              "entityID", mockProductId,
              "currentUser", expectedCurrentUser(at(productAvailabilityChangedEvent, "currentUser.claims.iat"))));

      assertDeepContains(productAvailabilityChangedEvent, expectedProductAvailabilityChangedEvent);
    }
  }

  private Map<String, Object> expectedCurrentUser(Object iat) {
    return object(
        "claims", object(
            "booster:role", "Admin",
            "email", adminEmail,
            "iat", iat,
            "id", adminEmail,
            "iss", "booster",
            "sub", adminEmail),
        "header", object(
            "alg", "RS256",
            "kid", "booster",
            "typ", "JWT"),
        "username", adminEmail,
        "roles", List.of("Admin"),
        "id", adminEmail);
  }

  private void createProductAndWaitForIt(String mockProductId) {
    client.mutate(CREATE_PRODUCT, object(
        "productID", mockProductId,
        "sku", alpha(10),
        "displayName", commerce.productName(),
        "description", lorem.paragraph(),
        "priceInCents", random.number(1),
        "currency", finance.currencyCode()));
    waitForIt(() -> applicationUnderTest.query().events("Product-" + mockProductId + "-event"),
        events -> events != null && events.size() == 1);
  }

  /** faker's {@code address.city()}. */
  private static String city() {
    return CITIES.get(ThreadLocalRandom.current().nextInt(CITIES.size()));
  }

  /** faker's {@code random.alpha({ count })}: lower-case letters. */
  private static String alpha(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> String.valueOf((char) ('a' + ThreadLocalRandom.current().nextInt(26))))
        .collect(Collectors.joining());
  }
}
