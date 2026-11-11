package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.sleep;
import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLError;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.common.Groups;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Port of {@code integration/provider-unaware/end-to-end/events.integration.ts}: the {@code eventsByType} and
 * {@code eventsByEntity} queries (the authorization matrix of the entities' {@code authorizeReadEvents}, the token
 * verification errors, order, structure, entity id, limit and time filters), {@code Booster.entitiesIDs} through the
 * {@code EntitiesIdsFinder} command (without limit and paginated one id at a time) and a global event handler.
 *
 * <p>{@code Query events ids} expects exactly the {@code Counter} and {@code AnotherCounter} entities this suite
 * creates (TypeScript's comment: "non-deterministic test as it needs an empty list of anotherCounter"); no other suite
 * creates them and the shared application starts with an empty store on every run. TypeScript skips {@code Query events
 * ids} on AWS; AWS is not ported, so the gates only document it.
 */
@EndToEnd
@DisplayName("Events end-to-end tests")
@SuppressWarnings("unchecked") // the responses are raw JSON trees (maps keyed by strings)
class EventsIT {

  private static final String AWS = "AWS";

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String ENTITIES_IDS_FINDER = """
      mutation EntitiesIdsFinder($entityName: String!, $limit: Float!) {
        EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit })
      }
      """;

  private static final String PAGINATED_ENTITIES_IDS_FINDER = """
      mutation EntitiesIdsFinder($entityName: String!, $limit: Float!, $afterCursor: JSON) {
        EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit, afterCursor: $afterCursor })
      }
      """;

  private static final String INCREMENT_COUNTER = """
      mutation IncrementCounter($counterId: ID!, $identifier: String!) {
        IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
      }
      """;

  private static final String LIST_COUNTER_READ_MODELS = """
      query ListCounterReadModels($filterBy: ListCounterReadModelFilter) {
        ListCounterReadModels(filter: $filterBy) {
          items {
            id
            identifier
            amount
          }
        }
      }
      """;

  private static final String INCREMENT_ANOTHER_COUNTER = """
      mutation IncrementAnotherCounter($anotherCounterId: ID!, $identifier: String!) {
        IncrementAnotherCounter(input: { anotherCounterId: $anotherCounterId, identifier: $identifier })
      }
      """;

  private static final String LIST_ANOTHER_COUNTER_READ_MODELS = """
      query ListAnotherCounterReadModels($filterBy: ListAnotherCounterReadModelFilter) {
        ListAnotherCounterReadModels(filter: $filterBy) {
          items {
            id
            identifier
            amount
          }
        }
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient anonymousClient;
  private GraphQLClient loggedClient;
  private GraphQLClient knowledgeableClient;
  private GraphQLClient expiredClient;
  private GraphQLClient beforeClient;
  private GraphQLClient expiredAndBeforeClient;

  EventsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    anonymousClient = applicationUnderTest.graphql().client();

    String userEmail = internet.email();
    String userToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail");
    loggedClient = applicationUnderTest.graphql().client(userToken);
    String tokenWithTheMagicWord = applicationUnderTest.token().forUser(userEmail, null,
        TokenOptions.builder().customClaim("magicWord", "opensesame").build());
    knowledgeableClient = applicationUnderTest.graphql().client(tokenWithTheMagicWord);
    String expiredToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail",
        TokenOptions.builder().expiresIn(0).build());
    expiredClient = applicationUnderTest.graphql().client(expiredToken);
    long notBefore = Dates.nowEpochMillis() / 1000 + 999999;
    String beforeToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail",
        TokenOptions.builder().notBefore(notBefore).build());
    beforeClient = applicationUnderTest.graphql().client(beforeToken);
    String expiredAndBeforeToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail",
        TokenOptions.builder().expiresIn(0).notBefore(notBefore).build());
    expiredAndBeforeClient = applicationUnderTest.graphql().client(expiredAndBeforeToken);
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  private static void expectRejectedWith(Runnable query, String regex) {
    assertThatThrownBy(query::run).isInstanceOf(GraphQLClientException.class).hasMessageFindingMatch(regex);
  }

  private static void expectFulfilled(Runnable query) {
    query.run();
  }

  private static void expectGraphQLErrors(Runnable query, String message, String code) {
    GraphQLClientException error = catchThrowableOfType(GraphQLClientException.class, query::run);

    assertThat(error).as("the query must be rejected").isNotNull().isInstanceOf(RuntimeException.class);
    assertThat(error.graphQLErrors())
        .extracting(GraphQLError::toMap)
        .containsExactly(Map.of("message", message, "extensions", Map.of("code", code)));
  }

  @Nested
  @Order(1)
  @DisplayName("Query events")
  class QueryEvents {

    @Nested
    @Order(1)
    @DisplayName("the authorization mechanism")
    class TheAuthorizationMechanism {

      @Nested
      @Order(1)
      @DisplayName("when querying events by type")
      class WhenQueryingEventsByType {

        @Nested
        @Order(1)
        @DisplayName("with a non-authenticated user")
        class WithANonAuthenticatedUser {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByType(anonymousClient, "OrderCreated"), "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to some roles")
          void canNotReadEventsBelongingToAnEntityAuthorizedToSomeRoles() {
            expectRejectedWith(() -> queryByType(anonymousClient, "ProductUpdated"),
                "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByType(anonymousClient, "CartItemChanged"));
          }

          @Test
          @Order(4)
          @DisplayName("can not read events from an entity with a custom authorizer")
          void canNotReadEventsFromAnEntityWithACustomAuthorizer() {
            expectRejectedWith(() -> queryByType(anonymousClient, "StockMoved"), "You don't know the magic word");
          }
        }

        @Nested
        @Order(2)
        @DisplayName("with an expired or not before token")
        class WithAnExpiredOrNotBeforeToken {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with an expired token")
          void canNotReadEventsBelongingToAnEntityWithAnExpiredToken() {
            expectGraphQLErrors(() -> queryByType(expiredClient, "CartItemChanged"),
                "TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired", "BoosterTokenExpiredError");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity with a token not before")
          void canNotReadEventsBelongingToAnEntityWithATokenNotBefore() {
            expectGraphQLErrors(() -> queryByType(beforeClient, "CartItemChanged"),
                "NotBeforeError: jwt not active\nNotBeforeError: jwt not active", "BoosterTokenNotBeforeError");
          }

          // jwt.verify checks NotBefore before Expired: a token both not active and expired gets the NotBefore error
          @Test
          @Order(3)
          @DisplayName("return BoosterTokenNotBeforeError with a token expired and not before")
          void returnBoosterTokenNotBeforeErrorWithATokenExpiredAndNotBefore() {
            expectGraphQLErrors(() -> queryByType(expiredAndBeforeClient, "CartItemChanged"),
                "NotBeforeError: jwt not active\nNotBeforeError: jwt not active", "BoosterTokenNotBeforeError");
          }
        }

        @Nested
        @Order(3)
        @DisplayName("with an authenticated user")
        class WithAnAuthenticatedUser {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByType(loggedClient, "OrderCreated"), "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to other role")
          void canNotReadEventsBelongingToAnEntityAuthorizedToOtherRole() {
            expectRejectedWith(() -> queryByType(loggedClient, "CartPaid"), "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByType(loggedClient, "CartItemChanged"));
          }

          @Test
          @Order(4)
          @DisplayName("can read events belonging to an entity authorized for their role")
          void canReadEventsBelongingToAnEntityAuthorizedForTheirRole() {
            expectFulfilled(() -> queryByType(loggedClient, "ProductUpdated"));
          }

          @Test
          @Order(5)
          @DisplayName("can not read events from an entity with a custom authorizer")
          void canNotReadEventsFromAnEntityWithACustomAuthorizer() {
            expectRejectedWith(() -> queryByType(loggedClient, "StockMoved"), "You don't know the magic word");
          }
        }

        @Nested
        @Order(4)
        @DisplayName("with a custom authorizer")
        class WithACustomAuthorizer {

          @Test
          @Order(1)
          @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByType(knowledgeableClient, "OrderCreated"),
                "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to a role")
          void canNotReadEventsBelongingToAnEntityAuthorizedToARole() {
            expectRejectedWith(() -> queryByType(knowledgeableClient, "CartPaid"), "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByType(knowledgeableClient, "CartItemChanged"));
          }

          @Test
          @Order(4)
          @DisplayName("can read events belonging to an entity that satisfies the custom authorizer")
          void canReadEventsBelongingToAnEntityThatSatisfiesTheCustomAuthorizer() {
            expectFulfilled(() -> queryByType(knowledgeableClient, "StockMoved"));
          }
        }
      }

      @Nested
      @Order(2)
      @DisplayName("when querying events by entity")
      class WhenQueryingEventsByEntity {

        @Nested
        @Order(1)
        @DisplayName("with a non-authenticated user")
        class WithANonAuthenticatedUser {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByEntity(anonymousClient, "Order"), "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to some roles")
          void canNotReadEventsBelongingToAnEntityAuthorizedToSomeRoles() {
            expectRejectedWith(() -> queryByEntity(anonymousClient, "Product"), "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByEntity(anonymousClient, "Cart"));
          }

          @Test
          @Order(4)
          @DisplayName("can not read events from an entity with a custom authorizer")
          void canNotReadEventsFromAnEntityWithACustomAuthorizer() {
            expectRejectedWith(() -> queryByEntity(anonymousClient, "Stock"), "You don't know the magic word");
          }
        }

        @Nested
        @Order(2)
        @DisplayName("with an authenticated user")
        class WithAnAuthenticatedUser {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByEntity(loggedClient, "Order"), "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to other role")
          void canNotReadEventsBelongingToAnEntityAuthorizedToOtherRole() {
            expectRejectedWith(() -> queryByEntity(loggedClient, "Payment"), "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByEntity(loggedClient, "Cart"));
          }

          @Test
          @Order(4)
          @DisplayName("can read events belonging to an entity authorized for their role")
          void canReadEventsBelongingToAnEntityAuthorizedForTheirRole() {
            expectFulfilled(() -> queryByEntity(loggedClient, "Product"));
          }

          @Test
          @Order(5)
          @DisplayName("can not read events from an entity with a custom authorizer")
          void canNotReadEventsFromAnEntityWithACustomAuthorizer() {
            expectRejectedWith(() -> queryByEntity(loggedClient, "Stock"), "You don't know the magic word");
          }
        }

        @Nested
        @Order(3)
        @DisplayName("with a custom authorizer")
        class WithACustomAuthorizer {

          @Test
          @Order(1)
          @DisplayName("can not read events belonging to an entity with no authorization declaration")
          void canNotReadEventsBelongingToAnEntityWithNoAuthorizationDeclaration() {
            expectRejectedWith(() -> queryByEntity(knowledgeableClient, "Order"), "Access denied for this resource");
          }

          @Test
          @Order(2)
          @DisplayName("can not read events belonging to an entity authorized to a role")
          void canNotReadEventsBelongingToAnEntityAuthorizedToARole() {
            expectRejectedWith(() -> queryByEntity(knowledgeableClient, "Payment"),
                "Access denied for this resource");
          }

          @Test
          @Order(3)
          @DisplayName("can read events belonging to an entity authorized for \"all\"")
          void canReadEventsBelongingToAnEntityAuthorizedForAll() {
            expectFulfilled(() -> queryByEntity(knowledgeableClient, "Cart"));
          }

          @Test
          @Order(4)
          @DisplayName("can read events belonging to an entity that satisfies the custom authorizer")
          void canReadEventsBelongingToAnEntityThatSatisfiesTheCustomAuthorizer() {
            expectFulfilled(() -> queryByEntity(knowledgeableClient, "Stock"));
          }
        }
      }
    }

    @Nested
    @Order(2)
    @DisplayName("the result of the queries")
    class TheResultOfTheQueries {
      private static final int NUMBER_OF_PROVISIONED_EVENTS = 10;
      private String mockCartId;
      private String mockProductId;
      private int mockQuantity;
      private long eventsProvisionedStartedAt;
      private long eventsProvisionedFinishedAt;

      @BeforeEach
      void provisionEvents() {
        mockCartId = random.uuid();
        mockProductId = random.uuid();
        mockQuantity = random.number(1);
        // Provision 10 events leaving like 100ms of time between them to have a clear time order.
        eventsProvisionedStartedAt = Dates.nowEpochMillis();
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          anonymousClient.mutate(CHANGE_CART_ITEM,
              variables("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
          sleep(100);
        }
        eventsProvisionedFinishedAt = Dates.nowEpochMillis();
      }

      /** {@code eventsProvisionedStartedAt} minus one second and {@code eventsProvisionedFinishedAt} plus one. */
      private Map<String, String> wholeProvisioningWindow() {
        return Map.of("from", Dates.toIso(eventsProvisionedStartedAt - 1000),
            "to", Dates.toIso(eventsProvisionedFinishedAt + 1000));
      }

      /** One second before the provisioning started to half of its duration. */
      private Map<String, String> firstHalfOfTheProvisioning() {
        long halfTheDuration = (eventsProvisionedFinishedAt - eventsProvisionedStartedAt) / 2;
        return Map.of("from", Dates.toIso(eventsProvisionedStartedAt - 1000),
            "to", Dates.toIso(eventsProvisionedStartedAt + halfTheDuration));
      }

      private void checkProvisionedEvents(List<Map<String, Object>> events) {
        for (Map<String, Object> event : events) {
          assertThat(event.get("type")).isEqualTo("CartItemChanged");
          assertThat(event.get("entityID")).isEqualTo(mockCartId);
          Map<String, Object> value = (Map<String, Object>) event.get("value");
          assertThat(value.get("productId")).isEqualTo(mockProductId);
          assertThat(value.get("quantity")).isEqualTo(mockQuantity);
        }
      }

      @Nested
      @Order(1)
      @DisplayName("when doing a query by entity")
      class WhenDoingAQueryByEntity {

        @Nested
        @Order(1)
        @DisplayName("with no time filters")
        class WithNoTimeFilters {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            GraphQLResponse result = queryByEntity(anonymousClient, "Cart");
            // As we are querying by just Entity, we will get the events we provisioned plus others (possible MANY
            // others) with different IDs. The only things we can check are:
            // - That, at least, we have "numberOfProvisionedEvents" events
            // - The structure is the expected one
            // - They came sorted by "createdAt" in descending order.
            List<Map<String, Object>> all = events(result, "eventsByEntity");
            List<Map<String, Object>> events = all.subList(0, Math.min(NUMBER_OF_PROVISIONED_EVENTS, all.size()));
            assertThat(events).hasSize(NUMBER_OF_PROVISIONED_EVENTS);
            checkOrderAndStructureOfEvents(events);
          }
        }

        @Nested
        @Order(2)
        @DisplayName("with time filters")
        class WithTimeFilters {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            // Fetch events with a time filter that goes from 1 second before the provisioning started to 1 seconds
            // after the provisioning finished. This way we will get, at least, all the events that were provisioned,
            // and that's that we will check
            //
            // The "one second before and after" is to leave enough room for possible misalignment between the
            // clocks (remember we are using the ISO format, so we can forget about timezones and all that
            // complicated stuff)
            GraphQLResponse result = queryByEntity(anonymousClient, "Cart", wholeProvisioningWindow());
            List<Map<String, Object>> events = events(result, "eventsByEntity");
            // First check the order and structure
            checkOrderAndStructureOfEvents(events);
            // Now check if the time filter worked well
            long provisionedEventsFound = events.stream().filter(e -> mockCartId.equals(e.get("entityID"))).count();
            assertThat(provisionedEventsFound).isGreaterThanOrEqualTo(NUMBER_OF_PROVISIONED_EVENTS);
          }
        }
      }

      @Nested
      @Order(2)
      @DisplayName("when doing a query by entity and entityID")
      class WhenDoingAQueryByEntityAndEntityID {

        @Nested
        @Order(1)
        @DisplayName("with no time filters")
        class WithNoTimeFilters {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            GraphQLResponse result = queryByEntity(anonymousClient, "Cart", null, mockCartId);
            List<Map<String, Object>> events = events(result, "eventsByEntity");
            // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
            assertThat(events).hasSize(NUMBER_OF_PROVISIONED_EVENTS);
            checkOrderAndStructureOfEvents(events);
            checkProvisionedEvents(events);
          }
        }

        @Nested
        @Order(2)
        @DisplayName("with limit")
        class WithLimit {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            int limit = 3;
            GraphQLResponse result = queryByEntity(anonymousClient, "Cart", null, mockCartId, limit);
            List<Map<String, Object>> events = events(result, "eventsByEntity");
            // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
            assertThat(events).hasSize(limit);
            checkOrderAndStructureOfEvents(events);
            checkProvisionedEvents(events);
          }
        }

        @Nested
        @Order(3)
        @DisplayName("with time filters")
        class WithTimeFilters {

          @Test
          @Order(1)
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            // Let's use a time filter that tries to get half of the events we provisioned. We can't be sure we will
            // get exactly half of them, because possible clock differences, but we will check using inequalities
            GraphQLResponse result =
                queryByEntity(anonymousClient, "Cart", firstHalfOfTheProvisioning(), mockCartId);
            List<Map<String, Object>> events = events(result, "eventsByEntity");
            // First check the order and structure
            checkOrderAndStructureOfEvents(events);
            // Now we check that we have received more than 0 events and less than number we provisioned, as time
            // filters we used should have given us less events than what we provisioned
            assertThat(events.size()).isBetween(1, NUMBER_OF_PROVISIONED_EVENTS - 1);
          }

          @Test
          @Order(2)
          @DisplayName("returns the expected events in the right order if we include limit and time filters and the "
              + "\"to\" is reached before the limit")
          void returnsTheExpectedEventsInTheRightOrderIfWeIncludeLimitAndTimeFiltersAndTheToIsReachedBeforeTheLimit() {
            // Let's use a time filter that tries to get half of the events we provisioned. We can't be sure we will
            // get exactly half of them, because possible clock differences, but we will check using inequalities
            GraphQLResponse result = queryByEntity(anonymousClient, "Cart", firstHalfOfTheProvisioning(), mockCartId,
                NUMBER_OF_PROVISIONED_EVENTS * 2);
            List<Map<String, Object>> events = events(result, "eventsByEntity");
            // First check the order and structure
            checkOrderAndStructureOfEvents(events);
            // Now we check that we have received more than 0 events and less than number we provisioned, as time
            // filters we used should have given us less events than what we provisioned
            assertThat(events.size()).isBetween(1, NUMBER_OF_PROVISIONED_EVENTS - 1);
          }
        }
      }

      @Nested
      @Order(3)
      @DisplayName("when doing a query by type")
      class WhenDoingAQueryByType {

        @Nested
        @Order(1)
        @DisplayName("with no time filters")
        class WithNoTimeFilters {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            GraphQLResponse result = queryByType(anonymousClient, "CartItemChanged");
            // As we are querying by just event type, we will get the events we provisioned plus others (possible
            // MANY others) with different IDs. The only things we can check are:
            // - That, at least, we have "numberOfProvisionedEvents" events
            // - The structure is the expected one
            // - They came sorted by "createdAt" in descenting order.
            List<Map<String, Object>> all = events(result, "eventsByType");
            List<Map<String, Object>> events = all.subList(0, Math.min(NUMBER_OF_PROVISIONED_EVENTS, all.size()));
            assertThat(events).hasSize(NUMBER_OF_PROVISIONED_EVENTS);
            checkOrderAndStructureOfEvents(events);
          }
        }

        @Nested
        @Order(2)
        @DisplayName("with time filters")
        class WithTimeFilters {

          @Test
          @DisplayName("returns the expected events in the right order")
          void returnsTheExpectedEventsInTheRightOrder() {
            // The structure and the reasons of why this test is this way are exactly the same as described in tests:
            // 'when doing a query by type'.'with time filters'.'returns the expected events in the right order'
            GraphQLResponse result = queryByType(anonymousClient, "CartItemChanged", wholeProvisioningWindow());
            List<Map<String, Object>> events = events(result, "eventsByType");
            // First check the order and structure
            checkOrderAndStructureOfEvents(events);
            // Now check if the time filter worked well
            long provisionedEventsFound = events.stream().filter(e -> mockCartId.equals(e.get("entityID"))).count();
            assertThat(provisionedEventsFound).isGreaterThanOrEqualTo(NUMBER_OF_PROVISIONED_EVENTS);
          }
        }
      }
    }

    @Nested
    @Order(3)
    @DisplayName("the result of the queries involving many events")
    class TheResultOfTheQueriesInvolvingManyEvents {
      private static final int NUMBER_OF_PROVISIONED_EVENTS = 50;
      private String mockCartId;

      @BeforeEach
      void provisionEvents() {
        List<Callable<GraphQLResponse>> mutationPromises = new ArrayList<>();
        mockCartId = random.uuid();
        String cartId = mockCartId;
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          mutationPromises.add(() -> anonymousClient.mutate(CHANGE_CART_ITEM,
              variables("cartId", cartId, "productId", random.uuid(), "quantity", 1)));
        }
        try {
          Promises.runAll(mutationPromises);
        } catch (RuntimeException e) {
          System.out.println(BoosterJson.toJson(Map.of("message", String.valueOf(e.getMessage()))));
          throw e;
        }
      }

      @Nested
      @DisplayName("when doing a query that would return many (150) events")
      class WhenDoingAQueryThatWouldReturnMany150Events {

        @Test
        @DisplayName("returns the expected result")
        void returnsTheExpectedResult() {
          System.out.println(mockCartId);
          GraphQLResponse result = waitForIt(
              () -> queryByEntity(anonymousClient, "Cart", null, mockCartId),
              response -> ((List<?>) response.get("eventsByEntity")).size() == NUMBER_OF_PROVISIONED_EVENTS);
          List<Map<String, Object>> events = events(result, "eventsByEntity");
          assertThat(events).hasSize(NUMBER_OF_PROVISIONED_EVENTS);
          checkOrderAndStructureOfEvents(events);
          for (Map<String, Object> event : events) {
            assertThat(event.get("type")).isEqualTo("CartItemChanged");
            assertThat(event.get("entityID")).isEqualTo(mockCartId);
          }
        }
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Query events ids")
  class QueryEventsIds {

    // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the
    // events search
    @Nested
    @Order(1)
    @DisplayName("without limit")
    class WithoutLimit {
      private static final int NUMBER_OF_PROVISIONED_EVENTS = 3;
      private String mockCounterId;
      private final List<String> mockCounterIds = new ArrayList<>();
      private String mockSameCounterId;
      private String mockIdentifier;

      @BeforeAll
      void before() {
        skipOnAws();
      }

      @BeforeEach
      void provisionCounters() {
        // Provision N events with same counterId
        mockSameCounterId = random.uuid();
        System.out.println("Adding " + NUMBER_OF_PROVISIONED_EVENTS + " events with id " + mockSameCounterId);
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          anonymousClient.mutate(INCREMENT_COUNTER,
              variables("counterId", mockSameCounterId, "identifier", mockSameCounterId));
        }
        String sameCounterId = mockSameCounterId;
        waitForIt(
            () -> anonymousClient.query(LIST_COUNTER_READ_MODELS,
                variables("filterBy", variables("identifier", variables("eq", sameCounterId)))),
            result -> firstItemHasAmount(result, "ListCounterReadModels.items", NUMBER_OF_PROVISIONED_EVENTS));

        // Provision N events with random counterId and same identifier
        mockIdentifier = random.uuid();
        System.out.println("Adding " + NUMBER_OF_PROVISIONED_EVENTS + " events with identifier " + mockIdentifier);
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          mockCounterId = random.uuid();
          mockCounterIds.add(mockCounterId);
          anonymousClient.mutate(INCREMENT_COUNTER,
              variables("counterId", mockCounterId, "identifier", mockIdentifier));
        }
        String identifier = mockIdentifier;
        waitForIt(
            () -> anonymousClient.query(LIST_COUNTER_READ_MODELS,
                variables("filterBy", variables("identifier", variables("eq", identifier)))),
            result -> hasItems(result, "ListCounterReadModels.items", NUMBER_OF_PROVISIONED_EVENTS));
      }

      @Test
      @DisplayName("Should return all elements")
      void shouldReturnAllElements() {
        GraphQLResponse result = anonymousClient.mutate(ENTITIES_IDS_FINDER,
            // limit could not be mockSameCounterId as the test could be run several times
            variables("entityName", "Counter", "limit", 99999));

        List<?> items = (List<?>) result.get("EntitiesIdsFinder.items");

        // counter with same id should be only 1
        List<String> currentEntitiesIds =
            items.stream().map(item -> (String) ((Map<String, Object>) item).get("entityID")).toList();
        assertThat(currentEntitiesIds.stream().filter(mockSameCounterId::equals).count()).isEqualTo(1);

        // all random counters should be returned
        assertThat(currentEntitiesIds).containsAll(mockCounterIds);
        List<String> distinctCurrentEntitiesIds = Groups.unique(currentEntitiesIds);
        assertThat(distinctCurrentEntitiesIds).hasSameSizeAs(currentEntitiesIds);

        // There are exactly the expected number of ids
        assertThat(currentEntitiesIds).hasSize(NUMBER_OF_PROVISIONED_EVENTS + 1);
      }
    }

    // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the
    // events search
    @Nested
    @Order(2)
    @DisplayName("paginated with limit 1")
    class PaginatedWithLimit1 {
      private static final int NUMBER_OF_PROVISIONED_EVENTS = 3;
      private String mockAnotherCounterId;
      private final List<String> mockAnotherCounterIds = new ArrayList<>();
      private String mockSameAnotherCounterId;
      private String mockIdentifier;

      @BeforeAll
      void before() {
        skipOnAws();
      }

      @BeforeEach
      void provisionAnotherCounters() {
        // Provision N events with same anotherCounterId
        mockSameAnotherCounterId = random.uuid();
        System.out.println("Adding " + NUMBER_OF_PROVISIONED_EVENTS + " events with id " + mockSameAnotherCounterId);
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          anonymousClient.mutate(INCREMENT_ANOTHER_COUNTER,
              variables("anotherCounterId", mockSameAnotherCounterId, "identifier", mockSameAnotherCounterId));
        }
        String sameAnotherCounterId = mockSameAnotherCounterId;
        waitForIt(
            () -> anonymousClient.query(LIST_ANOTHER_COUNTER_READ_MODELS,
                variables("filterBy", variables("identifier", variables("eq", sameAnotherCounterId)))),
            result -> firstItemHasAmount(result, "ListAnotherCounterReadModels.items", NUMBER_OF_PROVISIONED_EVENTS));

        // Provision N events with random anotherCounterId and same identifier
        mockIdentifier = random.uuid();
        System.out.println("Adding " + NUMBER_OF_PROVISIONED_EVENTS + " events with identifier " + mockIdentifier);
        for (int i = 0; i < NUMBER_OF_PROVISIONED_EVENTS; i++) {
          mockAnotherCounterId = random.uuid();
          mockAnotherCounterIds.add(mockAnotherCounterId);
          anonymousClient.mutate(INCREMENT_ANOTHER_COUNTER,
              variables("anotherCounterId", mockAnotherCounterId, "identifier", mockIdentifier));
        }
        String identifier = mockIdentifier;
        waitForIt(
            () -> anonymousClient.query(LIST_ANOTHER_COUNTER_READ_MODELS,
                variables("filterBy", variables("identifier", variables("eq", identifier)))),
            result -> hasItems(result, "ListAnotherCounterReadModels.items", NUMBER_OF_PROVISIONED_EVENTS));
      }

      @Test
      @DisplayName("Should return the exact number of pages")
      void shouldReturnTheExactNumberOfPages() {
        Object cursor = null;
        int count = 9999;
        int pages = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        while (count != 0) {
          Map<String, Object> variables = variables("entityName", "AnotherCounter", "limit", 1);
          if (cursor != null) {
            // the first call has `afterCursor: undefined`, which is not sent
            variables.put("afterCursor", cursor);
          }
          GraphQLResponse result = anonymousClient.mutate(PAGINATED_ENTITIES_IDS_FINDER, variables);

          cursor = result.get("EntitiesIdsFinder.cursor");
          count = ((Number) result.get("EntitiesIdsFinder.count")).intValue();

          if (count != 0) {
            pages++;
            ((List<?>) result.get("EntitiesIdsFinder.items")).forEach(item -> items.add((Map<String, Object>) item));
            System.out.println("Pages " + pages);
          }
        }
        assertThat(pages).isEqualTo(NUMBER_OF_PROVISIONED_EVENTS + 1);

        // counter with same id should be only 1
        List<String> currentEntitiesIds = items.stream().map(item -> (String) item.get("entityID")).toList();
        assertThat(currentEntitiesIds.stream().filter(mockSameAnotherCounterId::equals).count()).isEqualTo(1);

        // all random counters should be returned
        assertThat(currentEntitiesIds).containsAll(mockAnotherCounterIds);
        List<String> distinctCurrentEntitiesIds = Groups.unique(currentEntitiesIds);
        assertThat(distinctCurrentEntitiesIds).hasSameSizeAs(currentEntitiesIds);

        // There are exactly the expected number of ids
        assertThat(currentEntitiesIds).hasSize(NUMBER_OF_PROVISIONED_EVENTS + 1);
      }
    }

    private static void skipOnAws() {
      if (AWS.equals(AppHelper.testedProvider())) {
        System.out.println("****************** Warning **********************");
        System.out.println("AWS provider does not support entityIds Interface so these tests are skipped for AWS");
        System.out.println("*************************************************");
      }
      assumeFalse(AWS.equals(AppHelper.testedProvider()),
          "AWS provider does not support entityIds Interface so these tests are skipped for AWS");
    }

    /** {@code items?.length > 0 && items[0].amount === amount}. */
    private static boolean firstItemHasAmount(GraphQLResponse result, String path, int amount) {
      return result != null && result.get(path) instanceof List<?> items && !items.isEmpty()
          && ((Map<String, Object>) items.get(0)).get("amount") instanceof Number number
          && number.doubleValue() == amount;
    }

    /** {@code items?.length === size}. */
    private static boolean hasItems(GraphQLResponse result, String path, int size) {
      return result != null && result.get(path) instanceof List<?> items && items.size() == size;
    }
  }

  @Nested
  @Order(3)
  @DisplayName("Global event handler")
  class GlobalEventHandler {

    @Test
    @DisplayName("should update read model")
    void shouldUpdateReadModel() {
      String mockLogEventId = UUID.generate().toString();
      anonymousClient.mutate("""
          mutation LogEvent($logEventId: ID!, $value: String!) {
            LogEvent(input: { logEventId: $logEventId, value: $value })
          }
          """, variables("logEventId", mockLogEventId, "value", "test"));

      GraphQLResponse result = waitForIt(
          () -> anonymousClient.query("""
              query ListLogEventReceivedTestReadModels($filter: ListLogEventReceivedTestReadModelFilter) {
                ListLogEventReceivedTestReadModels(filter: $filter) {
                  items {
                    id
                    value
                  }
                }
              }
              """, variables("filter", variables("id", variables("eq", mockLogEventId)))),
          response -> response != null
              && response.get("ListLogEventReceivedTestReadModels.items") instanceof List<?> items
              && items.size() == 1);

      assertThat(result.get("ListLogEventReceivedTestReadModels.items[0].value")).isEqualTo("test");
    }
  }

  private static GraphQLResponse queryByType(GraphQLClient client, String type) {
    return queryByType(client, type, null, null);
  }

  private static GraphQLResponse queryByType(GraphQLClient client, String type, Map<String, String> timeFilters) {
    return queryByType(client, type, timeFilters, null);
  }

  private static GraphQLResponse queryByType(GraphQLClient client, String type, Map<String, String> timeFilters,
      Integer limit) {
    String queryTimeFilters =
        timeFilters != null ? ", from:\"" + timeFilters.get("from") + "\" to:\"" + timeFilters.get("to") + "\"" : "";
    String queryLimit = limit != null && limit != 0 ? ", limit:" + limit : "";
    return client.query("""
          query {
            eventsByType(type: %s%s%s) {
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
            }
          }
        """.formatted(type, queryTimeFilters, queryLimit));
  }

  private static GraphQLResponse queryByEntity(GraphQLClient client, String entity) {
    return queryByEntity(client, entity, null, null, null);
  }

  private static GraphQLResponse queryByEntity(GraphQLClient client, String entity, Map<String, String> timeFilters) {
    return queryByEntity(client, entity, timeFilters, null, null);
  }

  private static GraphQLResponse queryByEntity(GraphQLClient client, String entity, Map<String, String> timeFilters,
      String entityID) {
    return queryByEntity(client, entity, timeFilters, entityID, null);
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
            }
          }
        """.formatted(entity, queryEntityID, queryTimeFilters, queryLimit));
  }

  private static List<Map<String, Object>> events(GraphQLResponse result, String field) {
    return (List<Map<String, Object>>) result.get(field);
  }

  private static void checkOrderAndStructureOfEvents(List<Map<String, Object>> events) {
    // First check if they are in the right order (from more recent to older)
    List<Map<String, Object>> eventsSorted = new ArrayList<>(events);
    eventsSorted.sort(Comparator.comparing((Map<String, Object> event) -> (String) event.get("createdAt")).reversed());
    assertThat(eventsSorted).isEqualTo(events);
    // Now check if the structure and some of their fields are correct
    for (Map<String, Object> event : events) {
      assertThat(event.keySet()).containsExactlyInAnyOrder(
          "__typename", "createdAt", "entity", "entityID", "requestID", "type", "user", "value");
      assertThat(event.get("entity")).isEqualTo("Cart");
      // In this function, we can't check for specific values of type, entityID or value.productID because other
      // tests (and ScheduledCommands) could have created other events for the Cart entity with different values for
      // those properties. What we can check is only its presence:
      assertThat(event).containsKey("type");
      assertThat(event).containsKey("entityID");
      assertThat((Map<String, Object>) event.get("value")).containsKey("cartId");
    }
  }
}
