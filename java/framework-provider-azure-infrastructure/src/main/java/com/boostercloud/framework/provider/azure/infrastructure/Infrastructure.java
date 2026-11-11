package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Entry point of the Azure provider infrastructure ({@code export const Infrastructure} of {@code src/index.ts}),
 * loaded by the Azure provider through {@code ProviderLoader} as
 * {@code com.boostercloud.framework.provider.azure.infrastructure.Infrastructure.infrastructure(rockets)}.
 *
 * <p>As in TypeScript every rocket is loaded eagerly here ({@code RocketLoader.loadRocket}, which fails with the
 * TypeScript messages when a rocket package is missing or is not a rocket), before any operation runs. The returned
 * object supports {@code deploy}, {@code nuke} and {@code synth}; {@code start} is not supported.
 */
public final class Infrastructure {

  private Infrastructure() {}

  /**
   * {@code Infrastructure(rocketDescriptors)}.
   *
   * @param rocketDescriptors the rockets of the application, or {@code null}
   * @return the infrastructure
   */
  public static ProviderInfrastructure infrastructure(List<RocketDescriptor> rocketDescriptors) {
    return infrastructure(rocketDescriptors, descriptor -> RocketLoader.loadRocket(descriptor, InfrastructureRocket.class),
        InfrastructureContext::defaults);
  }

  /**
   * The same with injectable rocket loading and context (tests).
   *
   * @param rocketDescriptors the rockets, or {@code null}
   * @param loadRocket loads one rocket
   * @param contextFactory creates the context of each operation
   * @return the infrastructure
   */
  public static AzureInfrastructure infrastructure(
      List<RocketDescriptor> rocketDescriptors,
      Function<RocketDescriptor, InfrastructureRocket> loadRocket,
      Function<BoosterConfig, InfrastructureContext> contextFactory) {
    List<InfrastructureRocket> rockets = null;
    if (rocketDescriptors != null) {
      rockets = new ArrayList<>();
      for (RocketDescriptor descriptor : rocketDescriptors) {
        rockets.add(loadRocket.apply(descriptor));
      }
    }
    return new AzureInfrastructure(rockets, contextFactory);
  }
}
