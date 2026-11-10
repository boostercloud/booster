package com.boostercloud.framework.types.concepts;

import java.util.List;

/**
 * Port of the TypeScript {@code QueryFilterHooks} option.
 *
 * @param before the hooks, in execution order ({@code null} is treated as empty)
 */
public record QueryFilterHooks(List<QueryBeforeFunction> before) {
  /** Canonical constructor; {@code null} becomes an empty list. */
  public QueryFilterHooks {
    before = before == null ? List.of() : List.copyOf(before);
  }
}
