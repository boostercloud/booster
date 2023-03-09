/* eslint-disable @typescript-eslint/no-explicit-any */

export enum Level {
  debug = 0,
  info = 1,
  warn = 2,
  error = 3,
}

export type LevelString = keyof typeof Level

/**
 * Logger interface
 *
 * Implementations must handle serialization of the message and optionalParams
 * in the case that they are not strings.
 */
export abstract class Logger {
  /**
   * Log a debug message
   */
  abstract debug(message?: unknown, ...optionalParams: unknown[]): void

  /**
   * Log an info message
   */
  abstract info(message?: unknown, ...optionalParams: unknown[]): void

  /**
   * Log a warning message
   */
  abstract warn(message?: unknown, ...optionalParams: unknown[]): void

  /**
   * Log an error message
   */
  abstract error(message?: unknown, ...optionalParams: unknown[]): void

  // TODO: Move this to a separate interface that is in charge of logging the start and end of a process
  // As usually that's just something of the CLI, and in the core you will have the Tracer interface for
  // that, which will use Open Telemetry to log the start and end of a process.
  /**
   * Logs the start and the end of a function call, logging the
   * outcome of the function
   */
  abstract logProcess<T>(message: string, process: () => T): T
}

export type HasLogger = {
  logger: Logger
}
