package com.boostercloud.framework.core.integration.app.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;
import com.boostercloud.framework.core.integration.RecordingLogger;
import com.boostercloud.framework.core.integration.TestTokens;
import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import java.util.ArrayList;
import java.util.List;

/** The {@code test} environment: the in-memory provider, RS256 tokens and every health indicator enabled. */
public class StoreConfiguration implements BoosterConfigurator {
  @Override
  public void configure() {
    Booster.configure("test", config -> {
      config.appName = "integration-store";
      config.providerPackage = "com.boostercloud.framework.core.integration.inmemory";
      config.logger = RecordingLogger.INSTANCE;
      config.logLevel = Level.INFO;
      config.tokenVerifiers = new ArrayList<>(List.of(
          new PublicKeyTokenVerifier(TestTokens.ISSUER, TestTokens.publicKeyPem(), TestTokens.ROLES_CLAIM)));
      for (BoosterHealthIndicatorsIds id : List.of(BoosterHealthIndicatorsIds.ROOT, BoosterHealthIndicatorsIds.FUNCTION,
          BoosterHealthIndicatorsIds.DATABASE, BoosterHealthIndicatorsIds.DATABASE_EVENTS,
          BoosterHealthIndicatorsIds.DATABASE_READ_MODELS)) {
        config.sensorConfiguration.health.get(id).enabled = true;
      }
    });
    Booster.configure("production", config -> config.appName = "integration-store-production");
  }
}
