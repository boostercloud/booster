package com.boostercloud.framework.core;

/**
 * A configuration block of the application: the Java place of the TypeScript {@code src/config/config.ts} file, whose
 * {@code Booster.configure(...)} calls run when the file is imported. {@link Booster#start(Class)} finds every
 * concrete class implementing this interface in the scanned package, instantiates it with its no-argument
 * constructor and calls {@link #configure()} <b>before</b> registering the annotated classes.
 *
 * <pre>{@code
 * public class Config implements BoosterConfigurator {
 *   @Override public void configure() {
 *     Booster.configure("local", config -> {
 *       config.appName = "my-store";
 *       config.providerPackage = "com.boostercloud.framework.provider.local";
 *     });
 *   }
 * }
 * }</pre>
 */
public interface BoosterConfigurator {
  /** Configures the application, usually with {@link Booster#configure(String, java.util.function.Consumer)}. */
  void configure();
}
