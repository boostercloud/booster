export abstract class PackageManager {
  /**
   * Get the project root to be used by the package manager.
   */
  abstract getProjectRoot(): Promise<string>

  /**
   * Set the project root to be used by the package manager.
   */
  abstract setProjectRoot(projectRoot: string): Promise<void>

  /**
   * Run a script from the project's package.json
   */
  abstract runScript(scriptName: string, args: ReadonlyArray<string>): Promise<string>

  /**
   * Install the dependencies of the project in production mode.
   * This means that devDependencies will not be installed, and workspace packages will be
   * denormalized. This is done before deploying a project.
   */
  abstract installProductionDependencies(): Promise<void>

  /**
   * Install all the dependencies of the project.
   */
  abstract installAllDependencies(): Promise<void>

  /**
   * Build the project.
   */
  abstract build(args: ReadonlyArray<string>): Promise<string>

  /**
   * Returns the name of the lockfile used by the package manager.
   */
  abstract getLockfileName(): string
}
