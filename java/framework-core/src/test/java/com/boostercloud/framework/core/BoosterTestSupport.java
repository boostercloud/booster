package com.boostercloud.framework.core;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.EnvironmentVariables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base of the tests that touch the global {@link Booster} state: every test starts and ends with a fresh
 * configuration for the {@code test} environment (set by Surefire through {@code booster.env}), no environment
 * overrides and the system clock. The TypeScript tests clean the singleton in {@code afterEach}.
 */
public abstract class BoosterTestSupport {

  @BeforeEach
  void resetBoosterBefore() {
    resetAll();
  }

  @AfterEach
  void resetBoosterAfter() {
    resetAll();
  }

  /** Resets the global state. */
  public static void resetAll() {
    Booster.reset();
    EnvironmentVariables.clearOverrides();
    Dates.resetClock();
  }
}
