package com.boostercloud.framework.types.concepts;

import java.util.List;

/**
 * Port of the TypeScript {@code CommandFilterHooks} option.
 *
 * @param before the hooks, in execution order ({@code null} is treated as empty)
 */
public record CommandFilterHooks(List<CommandBeforeFunction> before) {
  /** Canonical constructor; {@code null} becomes an empty list. */
  public CommandFilterHooks {
    before = before == null ? List.of() : List.copyOf(before);
  }
}
