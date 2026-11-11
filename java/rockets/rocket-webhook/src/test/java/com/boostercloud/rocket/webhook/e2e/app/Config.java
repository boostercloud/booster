package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.application.tester.TokenHelper;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;
import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.rocket.webhook.core.BoosterWebhook;
import java.util.List;

/** The rocket configured as in the README: the local environment and an Azure one. */
public class Config implements BoosterConfigurator {

  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "webhook-e2e";
      config.providerPackage = "com.boostercloud.framework.provider.local";
      config.tokenVerifiers.add(new PublicKeyTokenVerifier("booster", TokenHelper.publicKeyPem(), "booster:role"));
      config.rockets = List.of(new BoosterWebhook(config, Webhooks.params()).rocketForLocal());
    });
    Booster.configure("production", config -> {
      config.appName = "webhook-e2e";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
      config.tokenVerifiers.add(new PublicKeyTokenVerifier("booster", TokenHelper.publicKeyPem(), "booster:role"));
      config.rockets = List.of(new BoosterWebhook(config, Webhooks.params()).rocketForAzure());
    });
  }
}
