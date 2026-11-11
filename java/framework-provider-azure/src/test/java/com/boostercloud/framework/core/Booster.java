package com.boostercloud.framework.core;

import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Test stand-in for core's {@code Booster} facade (framework-core is not a dependency of the provider): it records the
 * application classes passed to {@code start(Class)} and registers the {@link UserApp} produced by {@link #factory}.
 */
public final class Booster {

  /** Classes passed to {@link #start(Class)}, in order. */
  public static final List<Class<?>> STARTED = Collections.synchronizedList(new ArrayList<>());

  /** Builds the registered application. */
  public static volatile Function<Class<?>, UserApp> factory;

  private Booster() {}

  /**
   * The contract the Azure entry points rely on.
   *
   * @param appClass the application class
   */
  public static void start(Class<?> appClass) {
    STARTED.add(appClass);
    UserAppRegistry.register(factory.apply(appClass));
  }
}
