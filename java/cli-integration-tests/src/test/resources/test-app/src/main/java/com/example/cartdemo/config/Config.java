package com.example.cartdemo.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;

/** The environments of the application. */
public class Config implements BoosterConfigurator {
  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "cart-demo";
      config.providerPackage = "com.boostercloud.framework.provider.local";
      config.injectable = Injectables.INJECTABLE;
    });
  }
}
