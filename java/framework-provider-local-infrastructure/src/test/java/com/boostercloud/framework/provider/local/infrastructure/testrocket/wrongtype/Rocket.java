package com.boostercloud.framework.provider.local.infrastructure.testrocket.wrongtype;

import com.boostercloud.framework.common.RocketBuilder;

/** A builder whose rocket has a {@code mountStack} but is not a local infrastructure rocket (an Azure one, say). */
public class Rocket implements RocketBuilder {
  /** Not a {@code LocalInfrastructureRocket}. */
  public static final class AzureLikeRocket {
    public void mountStack(Object config, Object stack) {}
  }

  @Override
  public Object build(Object parameters) {
    return new AzureLikeRocket();
  }
}
