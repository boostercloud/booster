package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/scheduled-commands.integration.ts}: the scheduled command
 * {@code CheckCartCount} (every minute) checks the cart {@code the-checked-cart}, which then shows up in
 * {@code CartReadModel} with at least one check.
 */
@EndToEnd
@DisplayName("Scheduled commands")
class ScheduledCommandsFunctionalityIT {

  private final ApplicationUnderTest applicationUnderTest;

  ScheduledCommandsFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @Test
  @DisplayName("scheduled command ran and created a product")
  @SuppressWarnings("unchecked")
  void scheduledCommandRanAndCreatedAProduct() {
    GraphQLClient client = applicationUnderTest.graphql().client();
    String checkedCartId = "the-checked-cart";

    // Check that scheduled command created the new product
    GraphQLResponse cartData = waitForIt(
        () -> client.query("""
            query {
              CartReadModel(id: "%s") {
                id
                checks
              }
            }
            """.formatted(checkedCartId)),
        result -> result != null && result.get("CartReadModel") != null,
        10000,
        // CheckCartCount is run every minute, we need to give this test enough time to make sure that the cloud
        // provider does the first call
        90000);

    Map<String, Object> cartReadModel = (Map<String, Object>) cartData.get("CartReadModel");
    assertThat(cartReadModel).isNotNull();
    assertThat(cartReadModel.get("id")).isEqualTo(checkedCartId);
    assertThat(((Number) cartReadModel.get("checks")).doubleValue()).isGreaterThan(0);
  }
}
