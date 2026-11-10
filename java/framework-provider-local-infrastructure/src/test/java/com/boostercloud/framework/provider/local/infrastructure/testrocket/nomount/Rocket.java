package com.boostercloud.framework.provider.local.infrastructure.testrocket.nomount;

import com.boostercloud.framework.common.RocketBuilder;

/** A builder whose rocket has no {@code mountStack}. */
public class Rocket implements RocketBuilder {
  @Override
  public Object build(Object parameters) {
    return new Object();
  }
}
