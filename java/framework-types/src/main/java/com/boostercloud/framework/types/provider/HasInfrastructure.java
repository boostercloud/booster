package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;

/**
 * Port of {@code HasInfrastructure}: what a provider infrastructure module exports. In Java it is a class
 * {@code <providerPackage>.infrastructure.Infrastructure} with a
 * {@code public static ProviderInfrastructure infrastructure(List<RocketDescriptor> rockets)} method;
 * {@link ProviderLoader#loadInfrastructurePackage(String)} adapts that static method to this interface.
 */
@FunctionalInterface
public interface HasInfrastructure {
  /**
   * @param rockets the rockets of the application (may be empty)
   * @return the infrastructure
   */
  ProviderInfrastructure infrastructure(List<RocketDescriptor> rockets);
}
