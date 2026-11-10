package com.boostercloud.framework.provider.local.infrastructure.testrocket.failing;

import com.boostercloud.framework.common.RocketBuilder;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructureRocket;

/** A rocket whose {@code mountStack} fails. */
public class Rocket implements RocketBuilder {
  @Override
  public Object build(Object parameters) {
    return (LocalInfrastructureRocket) (config, router, metadata) -> {
      throw new IllegalStateException("Undefined route parameter [object Object]");
    };
  }
}
