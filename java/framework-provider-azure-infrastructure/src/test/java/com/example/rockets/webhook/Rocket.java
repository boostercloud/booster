package com.example.rockets.webhook;

import com.boostercloud.framework.common.RocketBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestRockets;

/** A rocket package loaded by {@code RocketLoader} in the tests: {@code <package>.Rocket}. */
public class Rocket implements RocketBuilder {

  /** The parameters received by the last build. */
  public static Object lastParameters;

  @Override
  public Object build(Object parameters) {
    lastParameters = parameters;
    return new TestRockets.WebhookRocket();
  }
}
