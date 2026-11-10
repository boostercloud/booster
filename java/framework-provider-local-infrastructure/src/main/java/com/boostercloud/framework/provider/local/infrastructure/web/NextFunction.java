package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * Express's {@code next} callback.
 */
@FunctionalInterface
public interface NextFunction {
  /**
   * {@code next(err)}: skips the remaining normal handlers and runs the next error handler; {@code next(null)} is
   * {@link #next()}.
   *
   * @param error the error, or {@code null}
   */
  void next(Throwable error);

  /**
   * {@code next()}: runs the next matching handler.
   */
  default void next() {
    next(null);
  }
}
