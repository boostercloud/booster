package com.boostercloud.framework.common;

/** Internal exception helpers. */
final class Exceptions {
  private Exceptions() {}

  /**
   * Rethrows any throwable without wrapping it (the TypeScript code propagates rejections unchanged).
   *
   * @param throwable the throwable
   * @param <E> inferred as an unchecked type
   * @return never returns; declared so callers can write {@code throw sneakyThrow(e)}
   * @throws E always
   */
  @SuppressWarnings("unchecked")
  static <E extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws E {
    throw (E) throwable;
  }
}
