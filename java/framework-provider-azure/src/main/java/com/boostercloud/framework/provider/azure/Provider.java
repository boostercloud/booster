package com.boostercloud.framework.provider.azure;

import com.boostercloud.framework.types.provider.HasInfrastructure;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;

/**
 * Entry point of the Azure provider ({@code export const Provider} of {@code index.ts}). Booster loads it from
 * {@code config.providerPackage = "com.boostercloud.framework.provider.azure"} and calls
 * {@link #provider(List)}.
 */
public final class Provider {

  /** The provider package ({@code config.providerPackage}). */
  public static final String PACKAGE = "com.boostercloud.framework.provider.azure";

  /** Maven coordinates of the infrastructure module (the TypeScript {@code <package name>-infrastructure}). */
  public static final String INFRASTRUCTURE_ARTIFACT = "com.boostercloud:booster-framework-provider-azure-infrastructure";

  private Provider() {}

  /**
   * {@code Provider(rockets)}: a new library on each call, all sharing the process-wide Azure clients.
   *
   * @param rockets the rockets of the application, or {@code null}
   * @return the provider library
   */
  public static ProviderLibrary provider(List<RocketDescriptor> rockets) {
    return new AzureProviderLibrary(rockets, AzureClients.shared());
  }

  /**
   * {@code loadInfrastructurePackage(packageName)}: {@code <packageName>.infrastructure.Infrastructure}.
   *
   * @param packageName the provider package
   * @return the infrastructure factory
   * @throws IllegalStateException when the infrastructure module is not on the classpath
   */
  public static HasInfrastructure loadInfrastructurePackage(String packageName) {
    return ProviderLoader.loadInfrastructurePackage(packageName);
  }
}
