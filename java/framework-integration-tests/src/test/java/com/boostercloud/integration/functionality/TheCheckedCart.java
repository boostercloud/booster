package com.boostercloud.integration.functionality;

import com.boostercloud.integration.harness.ApplicationUnderTest;
import java.util.function.LongSupplier;

/**
 * Counters that leave out the work of the scheduled command {@code CheckCartCount}, which registers a
 * {@code CartChecked} event for the cart {@value #ID} every minute (and so creates its {@code CartReadModel} in the
 * first minute of the application).
 *
 * <p>Two TypeScript tests expect an exact count delta: one new event ({@code events > should create an event in the
 * event store}) and one new cart read model ({@code entities > should be projected into a read model}). TypeScript
 * runs them against an application deployed long before, alongside the scheduled command; when a check lands between
 * the two counts the delta is two, never one, and the test waits until it times out. The shared Java application is
 * fresh and the minute ticks during the run, so these tests count everything but {@value #ID}: the delta is then the
 * test's own work, which is what the assertion means.
 */
final class TheCheckedCart {

  /** The cart {@code CheckCartCount} checks. */
  static final String ID = "the-checked-cart";

  private TheCheckedCart() {}

  /**
   * @param applicationUnderTest the application
   * @return {@code count().events()} without the events of {@value #ID}
   */
  static long eventsCount(ApplicationUnderTest applicationUnderTest) {
    return countWithout(applicationUnderTest.count()::events,
        () -> applicationUnderTest.query().events("Cart-" + ID + "-event").size());
  }

  /**
   * @param applicationUnderTest the application
   * @return {@code count().readModels("CartReadModel")} without the read model of {@value #ID}
   */
  static long cartReadModelsCount(ApplicationUnderTest applicationUnderTest) {
    return countWithout(() -> applicationUnderTest.count().readModels("CartReadModel"),
        () -> applicationUnderTest.query().readModels(ID, "CartReadModel").isEmpty() ? 0 : 1);
  }

  /** {@code total - excluded}, read again when the excluded part changed while the total was read. */
  private static long countWithout(LongSupplier total, LongSupplier excluded) {
    while (true) {
      long excludedBefore = excluded.getAsLong();
      long count = total.getAsLong();
      if (excluded.getAsLong() == excludedBefore) {
        return count - excludedBefore;
      }
    }
  }
}
