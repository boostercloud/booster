package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;

/**
 * Port of {@code QueryBeforeFunction}: a {@code before} hook of a query, applied like
 * {@link CommandBeforeFunction}.
 */
@FunctionalInterface
public interface QueryBeforeFunction {
  /**
   * @param input the raw query input
   * @param currentUser the user, {@code null} for anonymous requests
   * @return the (possibly replaced) input
   */
  Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser);
}
