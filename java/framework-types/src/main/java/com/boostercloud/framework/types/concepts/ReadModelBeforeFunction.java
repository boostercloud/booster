package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code ReadModelBeforeFunction}: a {@code before} hook of a read model, applied sequentially to the
 * request envelope (it may return a modified copy, e.g. with extra filters); throwing aborts the request.
 */
@FunctionalInterface
public interface ReadModelBeforeFunction {
  /**
   * @param readModelRequestEnvelope the request
   * @param currentUser the user, {@code null} for anonymous requests
   * @return the (possibly replaced) request
   */
  ReadModelRequestEnvelope apply(ReadModelRequestEnvelope readModelRequestEnvelope, UserEnvelope currentUser);
}
