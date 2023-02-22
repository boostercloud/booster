export abstract class Process {
  /**
   * Execute a command in a shell, returning the output
   */
  abstract exec(command: string, cwd?: string): Promise<string>

  /**
   * Get the current working directory
   */
  abstract cwd(): Promise<string>

  /**
   * Get an environment variable
   */
  abstract getEnvironmentVariable(name: string): Promise<string | undefined>

  /**
   * Get an environment variable, defaulting to a value if it's not set
   */
  abstract getEnvironmentVariableOrDefault(name: string, defaultValue: string): Promise<string>

  /**
   * Set an environment variable
   */
  abstract setEnvironmentVariable(name: string, value: string): Promise<void>
}
