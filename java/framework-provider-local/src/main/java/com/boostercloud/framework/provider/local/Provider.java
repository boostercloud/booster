package com.boostercloud.framework.provider.local;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.provider.HasInfrastructure;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;

/**
 * Entry point of the local provider, loaded by core from {@code config.providerPackage =
 * "com.boostercloud.framework.provider.local"} (see {@link ProviderLoader}). Port of {@code index.ts}.
 *
 * <p>{@link #provider(List)} is TypeScript's {@code Provider(rocketDescriptors)}: it returns a new wiring over the
 * process-wide {@link LocalProviderRuntime#shared() runtime} (TypeScript's module-level registries, user app handle
 * and GraphQL service), and starts the websocket server of that runtime once. The registries use
 * {@code .booster/*.json} in the working directory and the application registered in
 * {@code UserAppRegistry}.
 */
public final class Provider {

  /** The Java package of this provider ({@code config.providerPackage}). */
  public static final String PACKAGE_NAME = "com.boostercloud.framework.provider.local";

  /** The package of the local infrastructure ({@code <package.json name>-infrastructure} in TypeScript). */
  public static final String INFRASTRUCTURE_PACKAGE_NAME = PACKAGE_NAME + "." + ProviderLoader.INFRASTRUCTURE_PACKAGE;

  /** The Maven coordinates of the local infrastructure module. */
  public static final String INFRASTRUCTURE_ARTIFACT = "com.boostercloud:booster-framework-provider-local-infrastructure";

  private Provider() {}

  /**
   * {@code Provider(rocketDescriptors?)}.
   *
   * @param rocketDescriptors the rockets configured by the application (may be {@code null})
   * @return the provider library
   */
  public static ProviderLibrary provider(List<RocketDescriptor> rocketDescriptors) {
    return new LocalProvider(LocalProviderRuntime.shared(), rocketDescriptors, Provider::loadInfrastructurePackage);
  }

  /**
   * {@code loadInfrastructurePackage(packageName)}: loads {@code <packageName>.Infrastructure} (its static
   * {@code infrastructure(List<RocketDescriptor>)} factory).
   *
   * @param packageName the infrastructure package ({@link #INFRASTRUCTURE_PACKAGE_NAME})
   * @return the factory
   * @throws IllegalStateException when the class cannot be loaded
   */
  public static HasInfrastructure loadInfrastructurePackage(String packageName) {
    String suffix = "." + ProviderLoader.INFRASTRUCTURE_PACKAGE;
    if (!packageName.endsWith(suffix)) {
      throw new IllegalStateException("Expected an infrastructure package ending in '" + suffix + "' but got '"
          + packageName + "'");
    }
    return ProviderLoader.loadInfrastructurePackage(packageName.substring(0, packageName.length() - suffix.length()));
  }

  /**
   * Java-only: re-dispatches, through the registered application, the events of the shared runtime that were stored
   * before a crash but never dispatched. The local infrastructure calls it at start-up.
   *
   * @param config the configuration
   * @return the re-dispatched events
   */
  public static List<EventEnvelope> recoverUndispatchedEvents(BoosterConfig config) {
    return LocalProviderRuntime.shared().recoverUndispatchedEvents(config);
  }
}
