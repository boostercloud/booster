package com.boostercloud.framework.core.sensor.health.healthindicators;

import java.util.Map;

/**
 * Port of {@code sensor/health/health-indicators/os-info.ts} ({@code osInfo()} and its result interfaces
 * {@code OsInfoCpuResult}, {@code OsInfoMemoryResult}, {@code OsInfoResult}, which stage core-2b may model as
 * records): CPU and memory details of the function indicator.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.9.2).
 */
public final class OsInfo {

  private OsInfo() {}

  /**
   * Implemented by stage core-2b.
   *
   * @return {@code { cpus: [...], memory: { totalBytes, freeBytes } }}
   */
  public static Map<String, Object> osInfo() {
    throw new UnsupportedOperationException("not implemented yet: OsInfo#osInfo");
  }
}
