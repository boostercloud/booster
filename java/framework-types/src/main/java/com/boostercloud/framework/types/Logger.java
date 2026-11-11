package com.boostercloud.framework.types;

/**
 * Logger contract, with the {@code console}-like variadic signature of the TypeScript {@code Logger} interface:
 * {@code debug(message?, ...optionalParams)}.
 *
 * <p>Booster code does not use this interface directly but obtains a prefixed, level-gated logger from
 * {@code com.boostercloud.framework.common.Loggers.getLogger(config, location)}. A custom implementation can be set
 * in {@link BoosterConfig#logger}; it receives the prefix (for example {@code "[Booster]|EventStore#store: "}) as
 * {@code message} and the caller's arguments as {@code optionalParams}, unformatted.
 */
public interface Logger {
  /**
   * @param message the first argument (the prefix when called through {@code getLogger})
   * @param optionalParams further arguments, passed unformatted
   */
  void debug(Object message, Object... optionalParams);

  /**
   * @param message the first argument (the prefix when called through {@code getLogger})
   * @param optionalParams further arguments, passed unformatted
   */
  void info(Object message, Object... optionalParams);

  /**
   * @param message the first argument (the prefix when called through {@code getLogger})
   * @param optionalParams further arguments, passed unformatted
   */
  void warn(Object message, Object... optionalParams);

  /**
   * @param message the first argument (the prefix when called through {@code getLogger})
   * @param optionalParams further arguments, passed unformatted
   */
  void error(Object message, Object... optionalParams);

  /**
   * Logs at the given level.
   *
   * @param level the level
   * @param message the first argument
   * @param optionalParams further arguments
   */
  default void log(Level level, Object message, Object... optionalParams) {
    switch (level) {
      case DEBUG -> debug(message, optionalParams);
      case INFO -> info(message, optionalParams);
      case WARN -> warn(message, optionalParams);
      case ERROR -> error(message, optionalParams);
    }
  }

  /** A logger that discards everything (TypeScript's {@code noopLog}). */
  Logger NOOP =
      new Logger() {
        @Override
        public void debug(Object message, Object... optionalParams) {}

        @Override
        public void info(Object message, Object... optionalParams) {}

        @Override
        public void warn(Object message, Object... optionalParams) {}

        @Override
        public void error(Object message, Object... optionalParams) {}
      };
}
