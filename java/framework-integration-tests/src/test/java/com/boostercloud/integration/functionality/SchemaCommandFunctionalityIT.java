package com.boostercloud.integration.functionality;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/schema.command.integration.ts}: the {@code @NonExposed}
 * property {@code test} of {@code ChangeCartItem} is not part of its GraphQL input type.
 */
@EndToEnd
@DisplayName("schemas")
class SchemaCommandFunctionalityIT {

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  SchemaCommandFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @DisplayName("should return the expected schema for commands")
  class ShouldReturnTheExpectedSchemaForCommands {

    @Test
    @DisplayName("When there is an ignored parameter")
    @SuppressWarnings("unchecked")
    void whenThereIsAnIgnoredParameter() {
      GraphQLResponse queryResult = client.query("""
          query UniversalQuery {
            __type(name: "ChangeCartItemInput") {
              __typename
              name
              kind
              inputFields {
                __typename
                name
                type {
                  __typename
                  name
                  kind
                }
              }
            }
          }
          """);
      List<Object> fieldsNames = ((List<Map<String, Object>>) queryResult.get("__type.inputFields")).stream()
          .map(field -> field.get("name"))
          .toList();
      // chai's `have.members`: the same elements in any order
      assertThat(fieldsNames).containsExactlyInAnyOrder("cartId", "productId", "quantity");
    }
  }
}
