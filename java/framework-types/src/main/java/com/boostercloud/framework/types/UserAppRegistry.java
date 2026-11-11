package com.boostercloud.framework.types;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide holder of the running application's {@link UserApp}, the Java replacement of
 * {@code require(path.join(process.cwd(), 'dist', 'index.js'))}. Core registers it in {@code Booster.start}; providers
 * and their infrastructure read it.
 */
public final class UserAppRegistry {

  private static final AtomicReference<UserApp> CURRENT = new AtomicReference<>();

  private UserAppRegistry() {}

  /**
   * Registers (or replaces) the application.
   *
   * @param userApp the application entry points
   */
  public static void register(UserApp userApp) {
    CURRENT.set(Objects.requireNonNull(userApp, "userApp"));
  }

  /**
   * @return the registered application
   * @throws IllegalStateException when none is registered
   */
  public static UserApp get() {
    UserApp userApp = CURRENT.get();
    if (userApp == null) {
      throw new IllegalStateException(
          "No Booster application is registered. Make sure Booster.start(...) was called before using the provider");
    }
    return userApp;
  }

  /**
   * @return the registered application, if any
   */
  public static Optional<UserApp> find() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Removes the registered application (for tests). */
  public static void clear() {
    CURRENT.set(null);
  }
}
