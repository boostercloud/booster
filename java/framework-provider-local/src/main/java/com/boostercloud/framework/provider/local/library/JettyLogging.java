package com.boostercloud.framework.provider.local.library;

import java.util.ServiceLoader;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * Jetty (the websocket server here, the HTTP server of the local infrastructure) logs through SLF4J; Booster itself
 * does not. The framework ships no SLF4J provider: the application chooses one, or none. With none, SLF4J falls back
 * to its no-operation logger and prints a three-line notice about it on the first Jetty start; {@link #prepare()}
 * silences that notice, and only that case.
 */
public final class JettyLogging {

  /** SLF4J's property for the verbosity of its own diagnostics. */
  static final String SLF4J_INTERNAL_VERBOSITY = "slf4j.internal.verbosity";

  private JettyLogging() {}

  /**
   * Called before Jetty is first used. When the class path has no SLF4J provider (nor a pre-2.0 binding, nor one named
   * by the {@code slf4j.provider} property) and {@code slf4j.internal.verbosity} is not set, sets it to {@code ERROR},
   * so SLF4J's notice about falling back to the no-operation logger is not printed. With a provider (logback,
   * slf4j-simple...) nothing changes: Jetty logs through it and SLF4J reports as usual.
   */
  public static synchronized void prepare() {
    if (System.getProperty(SLF4J_INTERNAL_VERBOSITY) != null || System.getProperty("slf4j.provider") != null) {
      return;
    }
    ClassLoader loader = LoggerFactory.class.getClassLoader();
    try {
      if (ServiceLoader.load(SLF4JServiceProvider.class, loader).findFirst().isPresent()
          || (loader != null ? loader.getResource("org/slf4j/impl/StaticLoggerBinder.class")
              : ClassLoader.getSystemResource("org/slf4j/impl/StaticLoggerBinder.class")) != null) {
        return;
      }
    } catch (RuntimeException | java.util.ServiceConfigurationError e) {
      return;
    }
    System.setProperty(SLF4J_INTERNAL_VERBOSITY, "ERROR");
  }
}
