package com.boostercloud.framework.core.fixtures.app.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;

/** Configures the {@code test} environment of the fixture application. */
public class AppConfig implements BoosterConfigurator {
  /** How many times {@link #configure()} ran. */
  public static int runs;

  @Override
  public void configure() {
    runs++;
    Booster.configure("test", config -> {
      config.appName = "fixture-app";
      config.sensorConfiguration.health.booster.get("booster").enabled = true;
    });
    Booster.configure("production", config -> config.appName = "never-used");
  }
}
