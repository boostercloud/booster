package com.boostercloud.cli.services;

/**
 * The logging contract used by the CLI services. It is the subset of the framework-types {@code Logger} interface
 * the TypeScript CLI relies on; it is redeclared here because the CLI does not depend on the framework.
 */
public interface Logger {
  /**
   * Logs a debug message.
   *
   * @param message the message
   */
  void debug(String message);

  /**
   * Logs an informative message.
   *
   * @param message the message
   */
  void info(String message);

  /**
   * Logs a warning.
   *
   * @param message the message
   */
  void warn(String message);

  /**
   * Logs an error.
   *
   * @param message the message
   */
  void error(String message);
}
