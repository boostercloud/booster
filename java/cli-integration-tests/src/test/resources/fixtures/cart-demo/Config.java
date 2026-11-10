package com.example.base_package_placeholder.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;

/** The environments of the application. Select one with {@code boost <command> -e <environment>}. */
public class Config implements BoosterConfigurator {
  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "project_name_placeholder";
      config.providerPackage = "com.boostercloud.framework.provider.local";
    });

    Booster.configure("production", config -> {
      config.appName = "project_name_placeholder";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
    });
  }
}
