package com.boostercloud.framework.provider.local.infrastructure.testrocket;

import com.boostercloud.framework.common.RocketBuilder;

/** The entry class of a test rocket package: builds an {@link EchoRocket} with the descriptor parameters. */
public class Rocket implements RocketBuilder {
  @Override
  public Object build(Object parameters) {
    return new EchoRocket(parameters);
  }
}
