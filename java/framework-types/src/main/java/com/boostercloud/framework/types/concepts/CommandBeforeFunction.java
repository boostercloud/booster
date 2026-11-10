package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;

/**
 * Port of {@code CommandBeforeFunction}: a {@code before} hook of a command. Hooks run sequentially in declaration
 * order on the raw GraphQL input ({@code CommandInput = Record<string, any>}), each receiving the output of the
 * previous one; throwing aborts the command.
 */
@FunctionalInterface
public interface CommandBeforeFunction {
  /**
   * @param input the raw command input
   * @param currentUser the user, {@code null} for anonymous requests
   * @return the (possibly replaced) input
   */
  Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser);
}
