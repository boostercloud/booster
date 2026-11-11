package com.boostercloud.application.tester;

/**
 * Counts of the items a provider has stored for the application under test. Port of the TypeScript {@code Counters}
 * interface ({@code provider-test-helper.ts}); each {@code Promise<number>} is a blocking method.
 *
 * <p>Implementations that cannot count something return {@code -1} (the TypeScript local provider returns
 * {@code -1} for every counter). Failures are unchecked exceptions.
 */
public interface Counters {

  /**
   * @return the number of stored GraphQL subscriptions
   */
  long subscriptions();

  /**
   * @return the number of open WebSocket connections the provider has recorded
   */
  long connections();

  /**
   * @param readModelName the read model name (its simple class name)
   * @return the number of stored instances of that read model
   */
  long readModels(String readModelName);

  /**
   * @return the number of stored events
   */
  long events();
}
