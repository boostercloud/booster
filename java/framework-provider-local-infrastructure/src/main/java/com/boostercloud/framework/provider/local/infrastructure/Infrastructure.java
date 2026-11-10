package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the local infrastructure, loaded by the local provider ({@code provider.infrastructure()}) through
 * {@link ProviderLoader}: {@code com.boostercloud.framework.provider.local.infrastructure.Infrastructure}. Port of
 * {@code index.ts} ({@code Infrastructure}).
 */
public final class Infrastructure {

  private Infrastructure() {}

  /**
   * {@code Infrastructure(rocketDescriptors?)}: loads every rocket right away (so a missing or invalid rocket fails
   * here, with {@code RocketLoader}'s messages) and returns the infrastructure.
   *
   * @param rocketDescriptors the rockets of the application ({@code null} for none)
   * @return the infrastructure
   */
  public static LocalInfrastructure infrastructure(List<RocketDescriptor> rocketDescriptors) {
    return infrastructure(rocketDescriptors, LocalInfrastructure.Options.defaults());
  }

  /**
   * @param rocketDescriptors the rockets ({@code null} for none)
   * @param options how the server runs
   * @return the infrastructure
   */
  public static LocalInfrastructure infrastructure(List<RocketDescriptor> rocketDescriptors,
      LocalInfrastructure.Options options) {
    return new LocalInfrastructure(loadRockets(rocketDescriptors), options);
  }

  /**
   * {@code rocketDescriptors?.map(RocketLoader.loadRocket)}: each {@code <packageName>.Rocket} builder must build a
   * {@link LocalInfrastructureRocket}.
   *
   * @param rocketDescriptors the rockets ({@code null} for none)
   * @return the loaded rockets, in order
   * @throws IllegalStateException {@code Could not load the rocket infrastructure package <pkg>...},
   *     {@code Could not initialize rocket infrastructure package <pkg>...} or
   *     {@code The package <pkg> doesn't seem to be a rocket.}
   */
  public static List<LocalInfrastructureRocket> loadRockets(List<RocketDescriptor> rocketDescriptors) {
    List<LocalInfrastructureRocket> rockets = new ArrayList<>();
    if (rocketDescriptors != null) {
      for (RocketDescriptor descriptor : rocketDescriptors) {
        rockets.add(RocketLoader.loadRocket(descriptor, LocalInfrastructureRocket.class));
      }
    }
    return rockets;
  }
}
