package com.boostercloud.application.tester.graphql;

/**
 * What a query or mutation does with the {@code errors} of a GraphQL response. Port of Apollo Client's
 * {@code ErrorPolicy}; Booster's tests always use the default, {@link #NONE}.
 */
public enum ErrorPolicy {
  /** Any GraphQL error makes the operation throw a {@link GraphQLClientException} (Apollo's default). */
  NONE("none"),
  /** GraphQL errors are dropped and the (partial) data is returned. */
  IGNORE("ignore"),
  /** The response is returned with both its data and its errors. */
  ALL("all");

  private final String value;

  ErrorPolicy(String value) {
    this.value = value;
  }

  /**
   * @return Apollo's name of the policy
   */
  public String value() {
    return value;
  }
}
