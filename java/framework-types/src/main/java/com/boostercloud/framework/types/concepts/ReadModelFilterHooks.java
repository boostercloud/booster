package com.boostercloud.framework.types.concepts;

import java.util.List;

/**
 * Port of the TypeScript {@code ReadModelFilterHooks} option.
 *
 * @param before the hooks, in execution order ({@code null} is treated as empty)
 */
public record ReadModelFilterHooks(List<ReadModelBeforeFunction> before) {
  /** Canonical constructor; {@code null} becomes an empty list. */
  public ReadModelFilterHooks {
    before = before == null ? List.of() : List.copyOf(before);
  }
}
