import { BoosterConfig } from '@boostercloud/framework-types'
import { Target } from '../file-generator/target'

/**
 * Configuration for the project initialization
 */
export interface ProjectCreationConfig {
  projectName: string
  description: string
  version: string
  author: string
  homepage: string
  license: string
  repository: string
  providerPackageName: string
  boosterVersion: string
  default: boolean
  skipInstall: boolean
  skipGit: boolean
}

/**
 * Interface to interact with the user's project.
 */
export abstract class UserProject {
  /**
   * Creates a new project in the current directory
   *
   * @todo This method only will check if the directory exists and ask the user if it should be removed.
   *       It should also create the project in the future
   */
  abstract create(projectName: string, config: ProjectCreationConfig): Promise<void>

  /**
   * Performs all the required checks to ensure
   * that the project is a Booster project and
   * it is properly configured.
   */
  abstract performChecks(): Promise<void>

  /**
   * Returns the current environment, if it is not set
   * it will attempt to set it from all the possible means
   * and then return it.
   *
   * If after that it is still not set,
   * it throws an error.
   */
  abstract getEnvironment(): Promise<string>

  /**
   * Overrides the current environment with the given one
   */
  abstract overrideEnvironment(environment: string): Promise<void>

  /**
   * Creates an isolated project in order to prepare the project for deployment or testing
   */
  abstract createSandbox(additionalAssets?: ReadonlyArray<string>): Promise<void>

  /**
   * Removes the sandbox project created by `createSandbox`
   */
  abstract removeSandbox(): Promise<void>

  /**
   * Compiles the project
   */
  abstract compile(): Promise<void>

  /**
   * Compiles the project and loads the configuration
   */
  abstract loadConfig(): Promise<BoosterConfig>

  /**
   * Returns the path to the file of a given resource
   */
  abstract lookupResource<T>(target: Target<T>): Promise<{ resourcePath: string; exists: boolean }>

  /**
   * Returns the Booster version that the project is using
   */
  abstract getBoosterVersion(): Promise<string>
}
