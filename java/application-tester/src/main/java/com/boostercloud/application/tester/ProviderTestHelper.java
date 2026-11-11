package com.boostercloud.application.tester;

import java.util.Objects;

/**
 * The contract every provider's test helper fulfils so that {@link ApplicationTester} can talk to an application
 * deployed (or started) with that provider. Port of the TypeScript {@code ProviderTestHelper} interface
 * ({@code provider-test-helper.ts}); the TypeScript properties are accessor methods.
 *
 * <p>Providers implement it in their infrastructure module (TypeScript: {@code LocalTestHelper},
 * {@code AzureTestHelper}); {@link #of(ApplicationOutputs, Counters, Queries)} builds one from its parts.
 */
public interface ProviderTestHelper {

  /**
   * @return the application endpoints
   */
  ApplicationOutputs outputs();

  /**
   * @return the provider's counters
   */
  Counters counters();

  /**
   * @return the provider's raw queries
   */
  Queries queries();

  /**
   * Builds a helper from its three parts.
   *
   * @param outputs the application endpoints
   * @param counters the counters
   * @param queries the raw queries
   * @return an immutable helper returning those parts
   */
  static ProviderTestHelper of(ApplicationOutputs outputs, Counters counters, Queries queries) {
    return new SimpleProviderTestHelper(
        Objects.requireNonNull(outputs, "outputs"),
        Objects.requireNonNull(counters, "counters"),
        Objects.requireNonNull(queries, "queries"));
  }
}

/** The implementation behind {@link ProviderTestHelper#of}. */
record SimpleProviderTestHelper(ApplicationOutputs outputs, Counters counters, Queries queries)
    implements ProviderTestHelper {}
