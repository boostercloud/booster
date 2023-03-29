/**
 * TaskLogger interface
 *
 * Used to log the start, the end, and the outcome of a process.
 */
export abstract class TaskLogger {
  /**
   * Logs the start and the end of a function call, logging the
   * outcome of the function
   */
  abstract logTask<T>(message: string, task: () => T): Promise<T>
}
