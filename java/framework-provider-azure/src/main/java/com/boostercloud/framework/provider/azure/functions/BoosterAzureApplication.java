package com.boostercloud.framework.provider.azure.functions;

import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Boots the user application once per Azure Functions worker (the Java replacement of the generated
 * {@code functions.js}'s {@code require('./dist/index')}): reads the {@code BOOSTER_APP_CLASS} app setting, loads that
 * class and calls {@code com.boostercloud.framework.core.Booster.start(appClass)} reflectively (the provider does not
 * depend on core). {@code Booster.start} registers the application in {@link UserAppRegistry}, which the function
 * entry points then use. When an application is already registered (embedded use, tests) nothing is booted.
 */
public final class BoosterAzureApplication {

  /** The core facade whose {@code start(Class)} boots an application. */
  public static final String CORE_BOOSTER_CLASS = "com.boostercloud.framework.core.Booster";

  private static final Object LOCK = new Object();
  private static volatile Consumer<Class<?>> booter = BoosterAzureApplication::startWithCore;

  private BoosterAzureApplication() {}

  /**
   * @return the running application, booting it first when needed
   * @throws IllegalStateException when {@code BOOSTER_APP_CLASS} is missing or the application cannot be started
   */
  public static UserApp userApp() {
    Optional<UserApp> registered = UserAppRegistry.find();
    if (registered.isPresent()) {
      return registered.get();
    }
    synchronized (LOCK) {
      registered = UserAppRegistry.find();
      if (registered.isPresent()) {
        return registered.get();
      }
      String appClassName = EnvironmentVariables.get(OtherEnvironmentVarNames.BOOSTER_APP_CLASS)
          .filter(name -> !name.isBlank())
          .orElseThrow(() -> new IllegalStateException("The " + OtherEnvironmentVarNames.BOOSTER_APP_CLASS
              + " app setting must contain the fully qualified name of the Booster application class"));
      Class<?> appClass;
      try {
        appClass = Class.forName(appClassName.strip(), true, classLoader());
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("The Booster application class " + appClassName + " (app setting "
            + OtherEnvironmentVarNames.BOOSTER_APP_CLASS + ") could not be loaded", e);
      }
      booter.accept(appClass);
      return UserAppRegistry.get();
    }
  }

  /**
   * Replaces the boot step (tests). The default calls {@code Booster.start(appClass)} reflectively.
   *
   * @param newBooter receives the application class; it must register a {@link UserApp}
   */
  public static void useBooter(Consumer<Class<?>> newBooter) {
    booter = newBooter == null ? BoosterAzureApplication::startWithCore : newBooter;
  }

  static void startWithCore(Class<?> appClass) {
    Method start;
    try {
      Class<?> booster = Class.forName(CORE_BOOSTER_CLASS, true, classLoader());
      start = booster.getMethod("start", Class.class);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      throw new IllegalStateException("Could not find " + CORE_BOOSTER_CLASS + ".start(Class): make sure "
          + "booster-framework-core is packaged with the function app", e);
    }
    try {
      start.invoke(null, appClass);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Booster.start failed", e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot call " + CORE_BOOSTER_CLASS + ".start", e);
    }
  }

  private static ClassLoader classLoader() {
    ClassLoader context = Thread.currentThread().getContextClassLoader();
    return context != null ? context : BoosterAzureApplication.class.getClassLoader();
  }
}
