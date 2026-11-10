package com.boostercloud.integration.azurecontract;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.integration.Main;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The application under test with {@code BOOSTER_ENV=azure} inside the test JVM: the app settings or the deploying
 * machine's variables given to {@link #prepare(Map)} become {@link EnvironmentVariables} overrides, the
 * {@code booster.env} property selects the {@code azure} environment, and {@link #close()} puts everything back
 * (the previous overrides, the property, and a {@link Booster#reset()}), so that the suites sharing the failsafe JVM
 * keep their own environment ({@code BOOSTER_ENV=local}, {@code BOOSTER_APP_SUFFIX}).
 *
 * <p>Either the test starts the application itself ({@link #start()}, what {@code Booster.run} does before
 * {@code deploy}) or the Azure Functions entry points start it on their first invocation from the
 * {@code BOOSTER_APP_CLASS} app setting, as in a function app.
 */
final class InProcessAzureApplication implements AutoCloseable {

  /** The environment of the Azure suites ({@code npm run integration/azure-*}: {@code BOOSTER_ENV=azure}). */
  static final String ENVIRONMENT = "azure";

  private final Map<String, Optional<String>> previous = new LinkedHashMap<>();
  private final String previousEnvironmentProperty;

  private InProcessAzureApplication(Map<String, String> variables) {
    previousEnvironmentProperty = System.getProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    Booster.reset();
    System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, ENVIRONMENT);
    Map<String, String> all = new LinkedHashMap<>();
    all.put(EnvironmentVariables.BOOSTER_ENV, ENVIRONMENT);
    all.putAll(variables);
    all.forEach((name, value) -> {
      previous.put(name, EnvironmentVariables.get(name));
      EnvironmentVariables.set(name, value);
    });
  }

  /**
   * @param variables environment variables of the process (app settings, deploying machine settings)
   * @return the prepared application (not started)
   */
  static InProcessAzureApplication prepare(Map<String, String> variables) {
    return new InProcessAzureApplication(variables);
  }

  /**
   * {@code Booster.start(Main.class)} in the {@code azure} environment.
   *
   * @return the configuration
   */
  BoosterConfig start() {
    Booster.start(Main.class);
    return Booster.config();
  }

  @Override
  public void close() {
    Booster.reset();
    if (previousEnvironmentProperty == null) {
      System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    } else {
      System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, previousEnvironmentProperty);
    }
    previous.forEach((name, value) -> {
      if (value.isEmpty() || value.get().equals(System.getenv(name))) {
        EnvironmentVariables.unset(name);
      } else {
        EnvironmentVariables.set(name, value.get());
      }
    });
  }
}
