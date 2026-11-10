package com.boostercloud.framework.core.sensor.health;

import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-utils.ts}: pure functions over the id-keyed indicator registry (the tree is
 * derived from {@code /}-separated ids).
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.7).
 */
public final class HealthUtils {

  private HealthUtils() {}

  /**
   * @param healthProviders the registry
   * @param id an indicator id
   * @return its metadata (throws {@code Unexpected HealthProvider id <id>} when absent)
   */
  public static HealthIndicatorMetadata metadataFromId(Map<String, HealthIndicatorMetadata> healthProviders, String id) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#metadataFromId");
  }

  /**
   * @param healthProvider an indicator
   * @return the id of its parent ({@code ""} for roots)
   */
  public static String parentId(HealthIndicatorMetadata healthProvider) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#parentId");
  }

  /**
   * @param healthProviders the registry
   * @return the indicators without a {@code /} in their id, in registry order
   */
  public static List<HealthIndicatorMetadata> rootHealthProviders(Map<String, HealthIndicatorMetadata> healthProviders) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#rootHealthProviders");
  }

  /**
   * @param healthIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return its direct children (none when {@code showChildren} is off for it or an ancestor)
   */
  public static List<HealthIndicatorMetadata> childHealthProviders(
      HealthIndicatorMetadata healthIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#childHealthProviders");
  }

  /**
   * @param mainIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return whether it and all its registered ancestors are enabled
   */
  public static boolean isEnabled(
      HealthIndicatorMetadata mainIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#isEnabled");
  }

  /**
   * @param mainIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return whether it and all its registered ancestors show their children
   */
  public static boolean showChildren(
      HealthIndicatorMetadata mainIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    throw new UnsupportedOperationException("not implemented yet: HealthUtils#showChildren");
  }
}
