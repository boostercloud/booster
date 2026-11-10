package com.boostercloud.framework.core.sensor.health;

import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-utils.ts}: pure functions over the id-keyed indicator registry. The component
 * tree is derived from the ids: they are paths separated by {@value #PATH_SEPARATOR}, an id without separator is a
 * root and the parent of {@code a/b/c} is {@code a/b}. Every list keeps the registry (insertion) order.
 */
public final class HealthUtils {

  /** Separator of the segments of an indicator id ({@code PATH_SEPARATOR}). */
  public static final String PATH_SEPARATOR = "/";

  private HealthUtils() {}

  /**
   * {@code metadataFromId(healthProviders, id)}.
   *
   * @param healthProviders the registry
   * @param id an indicator id
   * @return its metadata
   * @throws IllegalStateException {@code Unexpected HealthProvider id <id>} when the registry has no such id
   */
  public static HealthIndicatorMetadata metadataFromId(Map<String, HealthIndicatorMetadata> healthProviders, String id) {
    HealthIndicatorMetadata healthProvider = healthProviders.get(id);
    if (healthProvider == null) {
      throw new IllegalStateException("Unexpected HealthProvider id " + id);
    }
    return healthProvider;
  }

  /**
   * {@code parentId(healthProvider)}: the id without its last segment.
   *
   * @param healthProvider an indicator
   * @return the id of its parent ({@code ""} for roots)
   */
  public static String parentId(HealthIndicatorMetadata healthProvider) {
    String[] childPath = split(healthProvider.healthIndicatorConfiguration().id);
    return String.join(PATH_SEPARATOR, Arrays.copyOf(childPath, Math.max(0, childPath.length - 1)));
  }

  /**
   * {@code rootHealthProviders(healthProviders)}.
   *
   * @param healthProviders the registry
   * @return the indicators whose id has a single segment, in registry order
   */
  public static List<HealthIndicatorMetadata> rootHealthProviders(Map<String, HealthIndicatorMetadata> healthProviders) {
    List<HealthIndicatorMetadata> roots = new ArrayList<>();
    for (HealthIndicatorMetadata healthProvider : healthProviders.values()) {
      if (split(healthProvider.healthIndicatorConfiguration().id).length == 1) {
        roots.add(healthProvider);
      }
    }
    return roots;
  }

  /**
   * {@code childHealthProviders(healthIndicatorMetadata, healthProviders)}.
   *
   * @param healthIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return its direct children in registry order; none when {@link #showChildren} is off for it or an ancestor
   */
  public static List<HealthIndicatorMetadata> childHealthProviders(
      HealthIndicatorMetadata healthIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    if (!showChildren(healthIndicatorMetadata, healthProviders)) {
      return new ArrayList<>();
    }
    String currentParentId = healthIndicatorMetadata.healthIndicatorConfiguration().id;
    List<HealthIndicatorMetadata> children = new ArrayList<>();
    for (HealthIndicatorMetadata healthProvider : healthProviders.values()) {
      if (parentId(healthProvider).equals(currentParentId)) {
        children.add(healthProvider);
      }
    }
    return children;
  }

  /**
   * {@code isEnabled(mainIndicatorMetadata, healthProviders)}: the flag of the given metadata, then the flags of its
   * ancestors found in the registry (the walk stops, with {@code true}, at the first ancestor id missing from the
   * registry).
   *
   * @param mainIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return whether it and all its registered ancestors are enabled
   */
  public static boolean isEnabled(
      HealthIndicatorMetadata mainIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    if (!mainIndicatorMetadata.healthIndicatorConfiguration().enabled) {
      return false;
    }
    HealthIndicatorMetadata parent = healthProviders.get(parentId(mainIndicatorMetadata));
    if (parent == null) {
      return true;
    }
    return isEnabled(parent, healthProviders);
  }

  /**
   * {@code showChildren(mainIndicatorMetadata, healthProviders)}: like {@link #isEnabled} for the
   * {@code showChildren} flag (an absent flag counts as {@code false}, TypeScript's falsy {@code undefined}).
   *
   * @param mainIndicatorMetadata an indicator
   * @param healthProviders the registry
   * @return whether it and all its registered ancestors show their children
   */
  public static boolean showChildren(
      HealthIndicatorMetadata mainIndicatorMetadata, Map<String, HealthIndicatorMetadata> healthProviders) {
    if (!Boolean.TRUE.equals(mainIndicatorMetadata.healthIndicatorConfiguration().showChildren)) {
      return false;
    }
    HealthIndicatorMetadata parent = healthProviders.get(parentId(mainIndicatorMetadata));
    if (parent == null) {
      return true;
    }
    return showChildren(parent, healthProviders);
  }

  /** JavaScript's {@code id.split('/')}: keeps empty segments, also trailing ones ({@code ''} gives one segment). */
  private static String[] split(String id) {
    return (id == null ? "" : id).split(PATH_SEPARATOR, -1);
  }
}
