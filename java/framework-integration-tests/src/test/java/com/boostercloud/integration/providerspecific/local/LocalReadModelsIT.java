package com.boostercloud.integration.providerspecific.local;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.random;
import static com.boostercloud.integration.providerspecific.local.LocalCommandsIT.SKIPPED_IN_TYPESCRIPT;
import static com.boostercloud.integration.providerspecific.local.LocalCommandsIT.changeCartItem;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-specific/local/read-models.integration.ts}: the {@code CartReadModel} a
 * {@code ChangeCartItem} projects into {@code .booster/read_models.json}.
 *
 * <p>Like {@link LocalCommandsIT}, no TypeScript npm script runs this file and its only {@code it} is an {@code xit}
 * without a reason, so it is {@link Disabled} here too, against the shared application. The NeDB read of
 * {@code read_models.json} ({@code findOne({'value.id': id})}) is the local test helper's raw query
 * ({@code query().readModels(id, "CartReadModel")}). Enabled, it fails in GraphQL validation like the tests of
 * {@link LocalCommandsIT}, whose {@code ChangeCartItem} document it shares.
 */
@EndToEnd
@DisplayName("read-models")
class LocalReadModelsIT {

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  LocalReadModelsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @Order(1)
  @DisplayName("valid command")
  class ValidCommand {
    private String mockCartId;
    private String mockProductId;
    private int mockQuantity;

    @BeforeEach
    void beforeEach() {
      mockCartId = random.uuid();
      mockProductId = random.uuid();
      mockQuantity = random.number(1);
    }

    @Test
    @Order(1)
    @Disabled(SKIPPED_IN_TYPESCRIPT)
    @DisplayName("should store read-models in the database")
    void shouldStoreReadModelsInTheDatabase() {
      changeCartItem(client, mockCartId, mockProductId, mockQuantity);

      // Wait until event is stored in database
      List<Map<String, Object>> readModels = waitForIt(
          () -> applicationUnderTest.query().readModels(mockCartId, "CartReadModel"),
          stored -> !stored.isEmpty() && stored.get(0) != null);

      // Verify the event content (chai's deep.include: every expected key with a deep-equal value)
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("productId", mockProductId);
      item.put("quantity", mockQuantity);
      Map<String, Object> expectedValue = new LinkedHashMap<>();
      expectedValue.put("id", mockCartId);
      expectedValue.put("cartItems", List.of(item));
      expectedValue.put("checks", 0);
      assertThat(readModels.get(0))
          .containsEntry("typeName", "CartReadModel")
          .containsEntry("value", expectedValue);
    }
  }
}
