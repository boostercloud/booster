package com.example.store.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;

public class Config implements BoosterConfigurator {
  @Override
  public void configure() {
    Booster.configure("test", config -> {
      config.appName = "my-store";
      config.providerPackage = "com.boostercloud.framework.provider.local";
    });
    Booster.configure("production", config -> {
      config.appName = "my-store";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
    });
  }
}
