package com.boostercloud.framework.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Port of {@code groups.ts}.
 */
public final class Groups {
  private Groups() {}

  /**
   * {@code unique(items)}: a new list without duplicates, keeping the first occurrence order. Equality is
   * {@link Object#equals(Object)} (JavaScript compares objects by reference; records compare by value here).
   *
   * @param items the items
   * @param <T> the item type
   * @return the distinct items
   */
  public static <T> List<T> unique(Collection<T> items) {
    return new ArrayList<>(new LinkedHashSet<>(items));
  }
}
