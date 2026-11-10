package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/queries.integration.ts}: custom {@code @Query} classes with a
 * scalar result ({@code CartTotalQuantity}, also through its before hook), a {@code JSON} result
 * ({@code CartsByCountry}, carts grouped by shipping country), a union of read models ({@code SearchMedia}) and a
 * query restricted to roles ({@code CartWithRole}, sent through {@code mutate} as in TypeScript).
 *
 * <p>{@code accepts a query with an object as result} expects exactly two Spanish and one Indian cart: like the
 * TypeScript suite it relies on a store where no other suite ships carts to {@code spain} or {@code india} (the
 * shared application starts with an empty store on every run).
 */
@EndToEnd
@DisplayName("Queries end-to-end tests")
class QueriesIT {

  private static final String CART_TOTAL_QUANTITY = """
      query CartTotalQuantity($cartId: ID!) {
        CartTotalQuantity(input: { cartId: $cartId })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;

  QueriesIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  /** {@code result?.data?.CartTotalQuantity != 0} (JavaScript loose inequality: a missing value is not 0). */
  private static boolean totalQuantityIsNotZero(GraphQLResponse result) {
    Object total = result == null ? null : result.get("CartTotalQuantity");
    return !(total instanceof Number number && number.doubleValue() == 0);
  }

  @Nested
  @Order(1)
  @DisplayName("with public queries")
  class WithPublicQueries {
    private GraphQLClient client;

    @BeforeAll
    void before() {
      client = applicationUnderTest.graphql().client();
    }

    @Test
    @Order(1)
    @DisplayName("accepts a query successfully")
    void acceptsAQuerySuccessfully() {
      String cartId = random.uuid();
      int quantity = random.number(1);
      client.mutate(CartIT.CHANGE_CART_ITEM,
          variables("cartId", cartId, "productId", random.uuid(), "quantity", quantity));

      GraphQLResponse response = waitForIt(
          () -> client.query(CART_TOTAL_QUANTITY, variables("cartId", cartId)),
          QueriesIT::totalQuantityIsNotZero);

      assertThat(response).isNotNull();
      assertThat(response.get("CartTotalQuantity")).isEqualTo(quantity);
    }

    @Test
    @Order(2)
    @DisplayName("accepts a query with an object as result")
    void acceptsAQueryWithAnObjectAsResult() {
      List<UUID> cartIds = new ArrayList<>();
      List<String> countries = Arrays.asList("spain", null, "india", "spain");
      // create 4 carts
      for (int i = 0; i < 4; i++) {
        cartIds.add(UUID.generate());
        client.mutate(CartIT.CHANGE_CART_ITEM,
            variables("cartId", cartIds.get(i).toString(), "productId", random.uuid(), "quantity", 10));

        if (countries.get(i) != null) {
          client.mutate("""
              mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
                UpdateShippingAddress(input: { cartId: $cartId, address: $address })
              }
              """, variables(
              "cartId", cartIds.get(i).toString(),
              "address", variables(
                  "firstName", "firstName",
                  "lastName", "lastName",
                  "country", countries.get(i),
                  "state", "state",
                  "postalCode", "11111",
                  "address", "address"),
              "quantity", 10));
        }
      }

      GraphQLResponse response = waitForIt(
          () -> client.query("""
              query CartsByCountry {
                CartsByCountry
              }
              """, Map.of()),
          result -> result != null && result.get("CartsByCountry") instanceof Map<?, ?> byCountry
              && byCountry.get("spain") instanceof List<?> spain && spain.size() == 2);

      assertThat(response).isNotNull();
      assertThat((List<?>) response.get("CartsByCountry.spain")).hasSize(2);
      assertThat((List<?>) response.get("CartsByCountry.india")).hasSize(1);
    }

    @Test
    @Order(3)
    @DisplayName("before hook multiply the value by beforeHookQueryMultiply")
    void beforeHookMultiplyTheValueByBeforeHookQueryMultiply() {
      String cartId = Constants.BEFORE_HOOK_QUERY_ID;
      int quantity = random.number(1);
      client.mutate(CartIT.CHANGE_CART_ITEM,
          variables("cartId", cartId, "productId", random.uuid(), "quantity", quantity));

      GraphQLResponse response = waitForIt(
          () -> client.query(CART_TOTAL_QUANTITY, variables("cartId", cartId)),
          QueriesIT::totalQuantityIsNotZero);

      assertThat(response).isNotNull();
      assertThat(response.get("CartTotalQuantity")).isEqualTo(Constants.BEFORE_HOOK_QUERY_MULTIPLY * quantity);
    }

    @Test
    @Order(4)
    @DisplayName("accepts a union type")
    void acceptsAUnionType() {
      String bookId = random.uuid();
      String movieId = random.uuid();
      client.mutate("""
          mutation AddBook($bookId: ID!, $title: String!, $pages: Float!) {
            AddBook(input: { id: $bookId, title: $title, pages: $pages })
          }
          """, variables("bookId", bookId, "title", "The Life Of J Robert Oppenheimer", "pages", 42));
      client.mutate("""
          mutation AddMovie($movieId: ID!, $title: String!) {
            AddMovie(input: { id: $movieId, title: $title })
          }
          """, variables("movieId", movieId, "title", "Oppenheimer (2023)"));

      GraphQLResponse response = waitForIt(
          () -> client.query("""
              query SearchMedia($searchword: String!) {
                SearchMedia(input: { searchword: $searchword }) {
                  results {
                    __typename
                    ... on BookReadModel {
                      title
                      pages
                    }
                    ... on MovieReadModel {
                      title
                    }
                  }
                }
              }
              """, variables("searchword", "Oppenheimer")),
          result -> result != null && result.get("SearchMedia") != null);
      assertThat(response).isNotNull();
    }
  }

  @Nested
  @Order(2)
  @DisplayName("when the query requires a specific role")
  class WhenTheQueryRequiresASpecificRole {

    @Test
    @Order(1)
    @DisplayName("rejects the command if the user does not have the required role")
    void rejectsTheCommandIfTheUserDoesNotHaveTheRequiredRole() {
      String authToken = applicationUnderTest.token().forUser("admin@example.com", "User");
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);

      assertThatThrownBy(() -> client.mutate("""
          query CartWithRole {
            CartWithRole
          }
          """, Map.of()))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageFindingMatch("Access denied for this resource");
    }

    @Test
    @Order(2)
    @DisplayName("accepts the command if the user has the required role")
    void acceptsTheCommandIfTheUserHasTheRequiredRole() {
      String authToken = applicationUnderTest.token().forUser("admin@example.com", "Admin");
      GraphQLClient client = applicationUnderTest.graphql().client(authToken);
      GraphQLResponse result = client.mutate("""
          query CartWithRole {
            CartWithRole
          }
          """, Map.of());

      assertThat(result).isNotNull();
      assertThat(result.get("CartWithRole")).isEqualTo(true);
    }
  }
}
