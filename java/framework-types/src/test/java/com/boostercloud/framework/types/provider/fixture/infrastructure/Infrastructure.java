package com.boostercloud.framework.types.provider.fixture.infrastructure;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;

/** A fake infrastructure module that only supports deploy. */
public final class Infrastructure {
  private Infrastructure() {}

  /** Deploy-only infrastructure. */
  public static final class DeployOnly implements ProviderInfrastructure {
    /** Rockets received. */
    public final List<RocketDescriptor> rockets;

    /** Number of deploys. */
    public int deploys;

    DeployOnly(List<RocketDescriptor> rockets) {
      this.rockets = rockets;
    }

    @Override
    public void deploy(BoosterConfig config) {
      deploys++;
    }
  }

  /**
   * @param rockets the rockets
   * @return the infrastructure
   */
  public static ProviderInfrastructure infrastructure(List<RocketDescriptor> rockets) {
    return new DeployOnly(rockets);
  }
}
