package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/entities.integration.ts}: a cart and its payment, two
 * entities, are projected into one stored {@code CartReadModel} document, read straight from the provider's store
 * ({@code applicationUnderTest.query().readModels(...)}, real on the local provider). The count of cart read models
 * leaves out the one the scheduled command creates (see {@link TheCheckedCart}).
 */
@EndToEnd
@DisplayName("entities")
class EntitiesFunctionalityIT {

  private static final String CART_READ_MODEL_NAME = "CartReadModel";

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CONFIRM_PAYMENT = """
      mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
        ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  EntitiesFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Test
  @DisplayName("should be projected into a read model")
  void shouldBeProjectedIntoAReadModel() {
    // without the cart of the scheduled command, which may appear during the test (see TheCheckedCart)
    long readModelItemsCount = TheCheckedCart.cartReadModelsCount(applicationUnderTest);

    String mockCartId = random.uuid();
    String mockProductId = random.uuid();
    int mockQuantity = random.number(1);
    String mockPaymentId = random.uuid();
    String mockConfirmationToken = random.alphaNumeric(10);

    client.mutate(CHANGE_CART_ITEM,
        Map.of("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

    waitForIt(() -> applicationUnderTest.query().readModels(mockCartId, CART_READ_MODEL_NAME),
        readModel -> mockCartId.equals(at(readModel, "[0].id"))
            && mockProductId.equals(at(readModel, "[0].cartItems[0].productId")));

    GraphQLResponse confirmPaymentResponse = client.mutate(CONFIRM_PAYMENT, Map.of("paymentId", mockPaymentId,
        "cartId", mockCartId, "confirmationToken", mockConfirmationToken));

    assertThat(confirmPaymentResponse).isNotNull();
    assertThat(confirmPaymentResponse.get("ConfirmPayment")).isEqualTo(true);

    long expectedReadModelItemsCount = readModelItemsCount + 1;
    waitForIt(() -> TheCheckedCart.cartReadModelsCount(applicationUnderTest),
        newReadModelItemsCount -> newReadModelItemsCount == expectedReadModelItemsCount);

    List<Map<String, Object>> latestReadModelItem = waitForIt(
        () -> applicationUnderTest.query().readModels(mockCartId, CART_READ_MODEL_NAME),
        readModel -> mockCartId.equals(at(readModel, "[0].id"))
            && at(readModel, "[0].payment") != null
            && mockProductId.equals(at(readModel, "[0].cartItems[0].productId")));

    assertThat(latestReadModelItem).isNotNull();
    assertThat(at(latestReadModelItem, "[0].id")).isEqualTo(mockCartId);
    assertThat(at(latestReadModelItem, "[0].cartItems[0].productId")).isEqualTo(mockProductId);
    assertThat(at(latestReadModelItem, "[0].cartItems[0].quantity")).isEqualTo(mockQuantity);
    // TypeScript: `to.be.undefined`, so the key must be absent (a stored null would fail as well)
    assertThat(StoredDocuments.objectAt(latestReadModelItem, "[0].cartItems[0]")).doesNotContainKey("shippingAddress");
    assertThat(at(latestReadModelItem, "[0].payment.id")).isEqualTo(mockPaymentId);
    assertThat(at(latestReadModelItem, "[0].payment.cartId")).isEqualTo(mockCartId);
    assertThat(at(latestReadModelItem, "[0].payment.confirmationToken")).isEqualTo(mockConfirmationToken);
  }
}
