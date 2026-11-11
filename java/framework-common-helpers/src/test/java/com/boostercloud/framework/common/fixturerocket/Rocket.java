package com.boostercloud.framework.common.fixturerocket;

import com.boostercloud.framework.common.RocketBuilder;

/** A rocket package used by the rocket loader tests. */
public final class Rocket implements RocketBuilder {
  /** Public no-argument constructor required by the loader. */
  public Rocket() {}

  /**
   * The infrastructure rocket.
   *
   * @param parameters the descriptor parameters
   */
  public record Infrastructure(Object parameters) {
    /** Mounts the rocket. */
    public void mountStack() {}
  }

  @Override
  public Object build(Object parameters) {
    return new Infrastructure(parameters);
  }
}
