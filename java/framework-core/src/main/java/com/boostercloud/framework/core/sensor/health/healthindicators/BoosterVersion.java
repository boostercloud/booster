package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code sensor/health/health-indicators/booster-version.ts} ({@code boosterVersion(config)}): the version of
 * the framework used by the application, {@code ""} when unknown; never throws.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.9.1).
 */
public final class BoosterVersion {

  private BoosterVersion() {}

  /**
   * Implemented by stage core-2b.
   *
   * @param config the configuration
   * @return the framework version, or {@code ""}
   */
  public static String boosterVersion(BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: BoosterVersion#boosterVersion");
  }
}
