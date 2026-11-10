package com.boostercloud.integration.config;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;
import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.instrumentation.TraceConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfigurationBase;
import com.boostercloud.integration.common.CustomLogger;
import com.boostercloud.integration.common.CustomTracer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The environments of the application ({@code src/config/config.ts}): {@code local} (local provider) and
 * {@code azure} (Azure provider). The TypeScript {@code development} and {@code production} environments use the AWS
 * provider, which is not ported, so they are not configured. Only the active environment's block runs, in the
 * TypeScript statement order (later statements override earlier ones).
 */
public class Config implements BoosterConfigurator {

  /** The local provider package. */
  public static final String LOCAL_PROVIDER = "com.boostercloud.framework.provider.local";
  /** The Azure provider package. */
  public static final String AZURE_PROVIDER = "com.boostercloud.framework.provider.azure";
  /** Issuer of the accepted tokens. */
  public static final String TOKEN_ISSUER = "booster";
  /** Claim holding the roles of the accepted tokens. */
  public static final String ROLES_CLAIM = "booster:role";
  /** The public key verifying the tokens, relative to the project directory. */
  public static final Path PUBLIC_KEY = Path.of("assets", "certs", "public.key");
  /** Name of the trace action of {@code ChangeCartItem.handle}. */
  public static final String CHANGE_CART_ITEM_HANDLER = "CHANGE_CART_ITEM_HANDLER";

  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "my-store";
      config.providerPackage = LOCAL_PROVIDER;
      config.tokenVerifiers = tokenVerifiers(config);
      config.enableSubscriptions = false;
      config.eventStreamConfiguration = EventStreamConfiguration.disabled();
      config.injectable = Injectables.INJECTABLE;
      configureInvocationsHandler(config);
      configureLogger(config);
      configureBoosterSensorHealth(config);
      configureEventHub(config);
      configureSubscriptions(config);
    });

    Booster.configure("azure", config -> {
      // An automatically generated app name suffix allows running the integration tests of different branches
      // concurrently (TypeScript: process.env.BOOSTER_APP_SUFFIX ?? 'default')
      String appNameSuffix = EnvironmentVariables.get("BOOSTER_APP_SUFFIX").orElse("default");
      // The app suffix must be copied to the deployed functions
      config.env.put("BOOSTER_APP_SUFFIX", appNameSuffix);
      config.appName = "my-store-" + appNameSuffix;
      config.providerPackage = AZURE_PROVIDER;
      config.assets = new ArrayList<>(List.of("assets", "assetFile.txt", "host.json"));
      config.tokenVerifiers = tokenVerifiers(config);
      config.injectable = Injectables.INJECTABLE;
      configureInvocationsHandler(config);
      configureLogger(config);
      configureBoosterSensorHealth(config);
      configureEventHub(config);
      configureSubscriptions(config);
    });
  }

  /**
   * The pair of verifiers of every environment with authentication: a {@link PublicKeyTokenVerifier} and a
   * {@link CustomPublicKeyTokenVerifier}, both with the key of {@code assets/certs/public.key} (read now, as
   * TypeScript reads it with {@code fs.readFileSync} when the configuration runs).
   *
   * @param config the configuration
   * @return the verifiers
   */
  static List<com.boostercloud.framework.types.concepts.TokenVerifier> tokenVerifiers(BoosterConfig config) {
    String publicKey = readPublicKey(config);
    return new ArrayList<>(List.of(
        new PublicKeyTokenVerifier(TOKEN_ISSUER, publicKey, ROLES_CLAIM),
        new CustomPublicKeyTokenVerifier(TOKEN_ISSUER, publicKey, ROLES_CLAIM)));
  }

  /**
   * Reads {@code <project>/assets/certs/public.key} (TypeScript: {@code path.join(__dirname, '..', '..', 'assets',
   * 'certs', 'public.key')}, the package root).
   *
   * @param config the configuration
   * @return the PEM text
   */
  static String readPublicKey(BoosterConfig config) {
    Path file = Path.of(config.userProjectRootPath()).resolve(PUBLIC_KEY);
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the public key " + file, e);
    }
  }

  static void configureLogger(BoosterConfig config) {
    config.logger = new CustomLogger();
  }

  static void configureInvocationsHandler(BoosterConfig config) {
    TraceConfiguration traceConfiguration = new TraceConfiguration();
    traceConfiguration.enableTraceNotification =
        List.of(TraceActionTypes.COMMAND_HANDLER.name(), CHANGE_CART_ITEM_HANDLER);
    traceConfiguration.includeInternal = false;
    traceConfiguration.onStart = CustomTracer::onStart;
    traceConfiguration.onEnd = CustomTracer::onEnd;
    config.traceConfiguration = traceConfiguration;
  }

  static void configureBoosterSensorHealth(BoosterConfig config) {
    for (HealthIndicatorConfigurationBase indicator : config.sensorConfiguration.health.booster.values()) {
      indicator.enabled = true;
    }
  }

  static void configureSubscriptions(BoosterConfig config) {
    config.enableSubscriptions = true;
  }

  static void configureEventHub(BoosterConfig config) {
    config.eventStreamConfiguration =
        EventStreamConfiguration.enabled(EventStreamConfiguration.Parameters.of("test", 3, 1));
  }
}
