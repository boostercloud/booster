package com.boostercloud.application.tester;

import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import java.util.Objects;

/**
 * Everything a test needs to exercise one running Booster application: GraphQL clients, plain HTTP endpoints, JWT
 * tokens, and the provider's counters and raw queries. Port of {@code ApplicationTester}
 * ({@code application-tester.ts}); the TypeScript properties are accessor methods.
 *
 * <pre>{@code
 * ApplicationTester applicationUnderTest = new ApplicationTester(providerTestHelper);
 * GraphQLClient client = applicationUnderTest.graphql().client(
 *     applicationUnderTest.token().forUser("admin@example.com", "Admin"));
 * GraphQLResponse response = client.mutate(CHANGE_CART_ITEM, Map.of("cartId", id, "productId", p, "quantity", 2));
 * long events = applicationUnderTest.count().events();
 * }</pre>
 */
public final class ApplicationTester {

  private final GraphQLHelper graphql;
  private final HttpHelper http;
  private final TokenHelper token;
  private final Counters count;
  private final Queries query;

  /**
   * Wires the helpers with the default GraphQL client options.
   *
   * @param providerTestHelper the provider's test helper
   * @throws IllegalStateException when the bundled private key cannot be read
   */
  public ApplicationTester(ProviderTestHelper providerTestHelper) {
    this(providerTestHelper, GraphQLClientOptions.defaults());
  }

  /**
   * Wires the helpers.
   *
   * @param providerTestHelper the provider's test helper
   * @param graphQLClientOptions options of the GraphQL clients
   * @throws IllegalStateException when the bundled private key cannot be read
   */
  public ApplicationTester(ProviderTestHelper providerTestHelper, GraphQLClientOptions graphQLClientOptions) {
    this(providerTestHelper, graphQLClientOptions, new TokenHelper());
  }

  /**
   * Wires the helpers with a custom token helper (for applications configured with another key pair).
   *
   * @param providerTestHelper the provider's test helper
   * @param graphQLClientOptions options of the GraphQL clients
   * @param tokenHelper the token helper
   */
  public ApplicationTester(
      ProviderTestHelper providerTestHelper, GraphQLClientOptions graphQLClientOptions, TokenHelper tokenHelper) {
    Objects.requireNonNull(providerTestHelper, "providerTestHelper");
    this.graphql = new GraphQLHelper(providerTestHelper, graphQLClientOptions);
    this.http = new HttpHelper(providerTestHelper);
    this.token = Objects.requireNonNull(tokenHelper, "tokenHelper");
    this.count = providerTestHelper.counters();
    this.query = providerTestHelper.queries();
  }

  /**
   * @return the GraphQL client factory ({@code applicationUnderTest.graphql})
   */
  public GraphQLHelper graphql() {
    return graphql;
  }

  /**
   * @return the HTTP endpoints ({@code applicationUnderTest.http})
   */
  public HttpHelper http() {
    return http;
  }

  /**
   * @return the JWT factory ({@code applicationUnderTest.token})
   */
  public TokenHelper token() {
    return token;
  }

  /**
   * @return the provider's counters, captured at construction ({@code applicationUnderTest.count})
   */
  public Counters count() {
    return count;
  }

  /**
   * @return the provider's raw queries, captured at construction ({@code applicationUnderTest.query})
   */
  public Queries query() {
    return query;
  }
}
