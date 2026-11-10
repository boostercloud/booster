package com.boostercloud.integration.endtoend;

import static com.boostercloud.integration.harness.Faker.commerce;
import static com.boostercloud.integration.harness.Faker.finance;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/commands.integration.ts}: public commands, a command with
 * roles, a command with a custom authorization policy and the read model migration command.
 *
 * <p>{@code fails when trying to migrate a non-existent read model} expects Cosmos DB's {@code Resource Not Found}
 * error: it runs on Azure only. The local provider (TypeScript and Java alike) has no per-read-model storage to be
 * missing, searches the empty set and answers {@code Migrated 0 NonExistentReadModel}, which the additional local test
 * pins.
 */
@EndToEnd
@DisplayName("Commands end-to-end tests")
class CommandsIT {

  static final String CREATE_PRODUCT = """
      mutation CreateProduct(
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            sku: $sku
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
      """;

  static final String LOG_SOMETHING = """
      mutation {
        LogSomething
      }
      """;

  static final String MIGRATE_ALL_READ_MODEL = """
      mutation MigrateAllReadModel($readModelName: String!) {
        MigrateAllReadModel(input: { readModelName: $readModelName })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;

  CommandsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  /** The 5-field {@code CreateProduct} variables of the suite. */
  private static Map<String, Object> productVariables() {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("sku", random.uuid());
    variables.put("displayName", commerce.product());
    variables.put("description", commerce.productDescription());
    variables.put("priceInCents", (int) Math.floor(Math.random() * 100 + 1));
    variables.put("currency", finance.currencyName());
    return variables;
  }

  @Nested
  @Order(1)
  @DisplayName("with public commands")
  class WithPublicCommands {
    private GraphQLClient client;

    @BeforeAll
    void before() {
      client = applicationUnderTest.graphql().client();
    }

    @Test
    @Order(1)
    @DisplayName("accepts a command successfully")
    void acceptsACommandSuccessfully() {
      GraphQLResponse response = client.mutate(CartIT.CHANGE_CART_ITEM,
          Map.of("cartId", random.uuid(), "productId", random.uuid(), "quantity", random.number(1)));

      assertThat(response).isNotNull();
      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
    }

    @Test
    @Order(2)
    @DisplayName("accepts an empty command")
    void acceptsAnEmptyCommand() {
      GraphQLResponse response = client.mutate("""
          mutation {
            EmptyCommand
          }
          """, Map.of());

      assertThat(response).isNotNull();
      assertThat(response.get("EmptyCommand")).isEqualTo("Empty command executed");
    }
  }

  @Nested
  @Order(2)
  @DisplayName("when the command requires a specific role")
  class WhenTheCommandRequiresASpecificRole {

    @Test
    @Order(1)
    @DisplayName("rejects the command if the user does not have the required role")
    void rejectsTheCommandIfTheUserDoesNotHaveTheRequiredRole() {
      String authToken = applicationUnderTest.token().forUser("admin@example.com", "User");
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);

      assertThatThrownBy(() -> client.mutate(CREATE_PRODUCT, productVariables()))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageFindingMatch("Access denied for this resource");
    }

    @Test
    @Order(2)
    @DisplayName("accepts the command if the user has the required role")
    void acceptsTheCommandIfTheUserHasTheRequiredRole() {
      String authToken = applicationUnderTest.token().forUser("admin@example.com", "Admin");
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);
      GraphQLResponse result = client.mutate(CREATE_PRODUCT, productVariables());

      assertThat(result).isNotNull();
      assertThat(result.get("CreateProduct")).isEqualTo(true);
    }
  }

  @Nested
  @Order(3)
  @DisplayName("when the command requires a custom authorization policy")
  class WhenTheCommandRequiresACustomAuthorizationPolicy {

    @Test
    @Order(1)
    @DisplayName("rejects the command when the policy is not satisfied")
    void rejectsTheCommandWhenThePolicyIsNotSatisfied() {
      String authToken = applicationUnderTest.token().forUser("logger@example.com", "User");
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);

      // the variables are sent as in TypeScript; the document does not use them
      assertThatThrownBy(() -> client.mutate(LOG_SOMETHING, productVariables()))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageFindingMatch("You are not allowed to log something");
    }

    @Test
    @Order(2)
    @DisplayName("accepts the command when the policy is satisfied")
    void acceptsTheCommandWhenThePolicyIsSatisfied() {
      String authToken = applicationUnderTest.token().forUser("logger@example.com", null,
          TokenOptions.builder().customClaim("canLogSomething", true).build());
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);
      GraphQLResponse result = client.mutate(LOG_SOMETHING, productVariables());

      assertThat(result).isNotNull();
      assertThat(result.get("LogSomething")).isEqualTo(true);
    }
  }

  @Nested
  @Order(4)
  @DisplayName("with migration commands")
  class WithMigrationCommands {
    private GraphQLClient client;

    @BeforeAll
    void before() {
      client = applicationUnderTest.graphql().client();
    }

    @Test
    @Order(1)
    @DisplayName("successfully migrates all read models for a specific read model")
    void successfullyMigratesAllReadModelsForASpecificReadModel() {
      GraphQLResponse response = client.mutate(MIGRATE_ALL_READ_MODEL, Map.of("readModelName", "BookReadModel"));

      assertThat(response).isNotNull();
      assertThat(response.get("MigrateAllReadModel")).isInstanceOf(String.class);
      assertThat((String) response.get("MigrateAllReadModel")).contains("Migrated").contains("BookReadModel");
    }

    @Test
    @Order(2)
    @DisplayName("successfully migrates all read models for a different read model")
    void successfullyMigratesAllReadModelsForADifferentReadModel() {
      GraphQLResponse response = client.mutate(MIGRATE_ALL_READ_MODEL, Map.of("readModelName", "MovieReadModel"));

      assertThat(response).isNotNull();
      assertThat(response.get("MigrateAllReadModel")).isInstanceOf(String.class);
      assertThat((String) response.get("MigrateAllReadModel")).contains("Migrated").contains("MovieReadModel");
    }

    @Test
    @Order(3)
    @DisplayName("fails when trying to migrate a non-existent read model")
    void failsWhenTryingToMigrateANonExistentReadModel() {
      assumeFalse(AppHelper.LOCAL.equals(AppHelper.testedProvider()),
          "'Resource Not Found' is the error of Cosmos DB (Azure) for a missing read model container; the local "
              + "provider answers 'Migrated 0 NonExistentReadModel' (TypeScript and Java)");

      assertThatThrownBy(() -> client.mutate(MIGRATE_ALL_READ_MODEL, Map.of("readModelName", "NonExistentReadModel")))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageFindingMatch("Resource Not Found");
    }

    @Test
    @Order(4)
    @DisplayName("migrates nothing for a non-existent read model on the local provider")
    void migratesNothingForANonExistentReadModelOnTheLocalProvider() {
      assumeTrue(AppHelper.LOCAL.equals(AppHelper.testedProvider()), "local provider behaviour");

      GraphQLResponse response = client.mutate(MIGRATE_ALL_READ_MODEL, Map.of("readModelName", "NonExistentReadModel"));

      assertThat(response.get("MigrateAllReadModel")).isEqualTo("Migrated 0 NonExistentReadModel");
    }
  }
}
