package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code FlusherFunction = (record: Register) => Promise<void>}: stores the events of a register. The
 * production flusher is core's {@code RegisterHandler::flush}.
 */
@FunctionalInterface
public interface FlusherFunction {
  /**
   * @param register the register whose {@link Register#eventList()} must be stored
   */
  void flush(Register register);
}
