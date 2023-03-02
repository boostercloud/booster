import { Component } from '../../common/component'
import { ProjectCreationConfig, UserProject } from '.'
import { BoosterApp, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Process } from '../process'
import { CliError, ImpossibleError } from '../../common/errors'
import { FileSystem } from '../file-system'
import { DynamicImporter } from '../dynamic-importer'
import * as path from 'path'
import * as semver from 'semver'
import { PackageManager } from '../package-manager'
import { UserInput } from '../user-input'
import Brand from '../../common/brand'
import { projectTemplates } from '../../templates/project'
import { FileGenerator } from '../file-generator'

@Component({ throws: CliError })
export class LocalUserProject implements UserProject {
  private _environment: string | undefined
  private _projectDir: string | undefined
  private _sandboxPath = '.deploy'
  private _config: BoosterConfig | undefined
  public cliVersion: string | undefined

  constructor(
    readonly logger: Logger,
    readonly process: Process,
    readonly fileSystem: FileSystem,
    readonly dynamicImporter: DynamicImporter,
    readonly packageManager: PackageManager,
    readonly userInput: UserInput,
    readonly fileGenerator: FileGenerator
  ) {}

  async inSandboxRun(callback: () => Promise<void>): Promise<void> {
    await this.createSandbox()
    const cwd = await this.process.cwd()
    this._projectDir = this._projectDir ?? cwd
    const oldProjectDir = this._projectDir
    this._projectDir = path.join(this._projectDir, this._sandboxPath)
    await this.process.chdir(this._projectDir)
    await callback()
    await this.process.chdir(oldProjectDir)
    this._projectDir = oldProjectDir
    await this.removeSandbox()
  }

  async catch(e: unknown): Promise<CliError> {
    if (e instanceof CliError) return e
    return new CliError('ProjectConfigurationError', 'An unknown error occurred', e)
  }

  async create(projectName: string, config: ProjectCreationConfig): Promise<void> {
    // Check that the name is correct
    this.logger.debug('Checking that the project name is valid...')
    await this.assertNameIsCorrect(projectName)

    // Ensure the project directory does not exist
    this.logger.debug('Checking that the project directory does not exist...')
    const projectDir = path.join(process.cwd(), projectName)
    const dirExists = await this.fileSystem.exists(projectDir)
    if (dirExists) {
      this.logger.debug('The project directory already exists. Asking for user confirmation...')
      const shouldRemove = await this.userInput.defaultBoolean(
        Brand.dangerize(`The directory ${projectDir} already exists. Do you want to remove it?`)
      )
      if (shouldRemove) {
        this.logger.debug('Removing the project directory...')
        await this.fileSystem.remove(projectDir, { recursive: true })
      }
      throw new CliError(
        'GeneratorError',
        `The directory ${projectDir} already exists. Please use another project name`
      )
    }

    // Create the project directory
    this.logger.info('Creating project root')
    const cwd = await this.process.cwd()
    const dirs = [
      'commands',
      'common',
      'config',
      'entities',
      'events',
      'event-handlers',
      'read-models',
      'notifications',
      'migrations',
      'scheduled-commands',
    ]
    for (const dir of dirs) {
      const fullDir = path.join(cwd, projectDir, dir)
      this.logger.debug(`Creating directory ${fullDir}`)
      await this.fileSystem.makeDirectory(fullDir, { recursive: true })
    }

    // Generate the config files
    this.logger.info('Generating project configuration files')
    for (const [filepath, template] of projectTemplates) {
      const fullPath = path.join(cwd, projectDir, filepath)
      const name = path.basename(filepath)
      const extension = path.extname(filepath)
      const placementDir = path.dirname(filepath)
      this.logger.debug(`Creating file ${fullPath}`)
      await this.fileGenerator.generate({
        name,
        extension,
        placementDir,
        template,
        info: config,
      })
    }

    // Install dependencies (if needed)
    if (!config.skipInstall) {
      this.logger.info('Installing dependencies')
      await this.packageManager.setProjectRoot(projectDir)
      await this.packageManager.installAllDependencies()
    }

    // Initialize git repo (if needed)
    if (!config.skipGit) {
      this.logger.info('Initializing git repository')
      await this.packageManager.setProjectRoot(projectDir)
      await this.process.exec('git init && git add -A && git commit -m "Initial commit"')
    }

    this.logger.info('Project created successfully!')
  }

  async getEnvironment(): Promise<string> {
    if (this._environment) return this._environment
    const env = await this.process.getEnvironmentVariable('BOOSTER_ENV')
    if (env) {
      this._environment = env
      return env
    }
    throw new CliError(
      'NoEnvironmentSet',
      'Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.'
    )
  }

  async overrideEnvironment(environment: string): Promise<void> {
    await this.process.setEnvironmentVariable('BOOSTER_ENV', environment)
    this._environment = environment
  }

  async createSandbox(additionalAssets: ReadonlyArray<string> = []): Promise<void> {
    try {
      const cwd = await this.process.cwd()
      this.logger.info('Creating sandbox...')
      await this.removeSandbox()

      this.logger.debug('Creating new sandbox...')
      await this.fileSystem.makeDirectory(this._sandboxPath, { recursive: true })

      this.logger.debug('Copying source folder...')
      await this.fileSystem.copy('src', `${this._sandboxPath}/src`)

      this.logger.debug('Copying project configuration files...')
      const projectConfigFiles = ['package.json', 'tsconfig.json']
      projectConfigFiles.push(this.packageManager.getLockfileName())
      for (const projectFile of projectConfigFiles) {
        this.logger.debug(`Copying ${projectFile}...`)
        await this.fileSystem.copy(projectFile, `${this._sandboxPath}/${projectFile}`)
      }

      this.logger.debug('Copying additional assets...')
      for (const asset of additionalAssets) {
        this.logger.debug(`Copying ${asset}...`)
        await this.fileSystem.copy(asset, `${this._sandboxPath}/${asset}`)
      }

      this.logger.debug('Installing dependencies in production mode...')
      await this.packageManager.setProjectRoot(this._sandboxPath)
      await this.packageManager.installProductionDependencies()
      await this.packageManager.setProjectRoot(this._projectDir ?? cwd)
    } catch (e) {
      throw new CliError('SandboxCreationError', 'Error creating the sandbox: ' + e.message)
    }
  }

  async removeSandbox(): Promise<void> {
    this.logger.debug('Deleting old sandbox...')
    await this.fileSystem.remove(this._sandboxPath, { recursive: true, force: true })
  }

  async compile(): Promise<void> {
    await this.performChecks()
    this.logger.info('Compiling project')
    await this.packageManager.build([])
  }

  async loadConfig(): Promise<BoosterConfig> {
    if (this._config) return this._config
    await this.compile()
    const projectDir = await this.getAbsoluteProjectDir()
    const projectIndexPath = path.join(projectDir, 'dist', 'index.js')
    const currentEnv = await this.getEnvironment()
    const { Booster: app } = await this.dynamicImporter.import<{ Booster: BoosterApp }>(projectIndexPath)
    this._config = await new Promise((resolve) =>
      app.configureCurrentEnv((config) => {
        checkEnvironmentWasConfigured(app, currentEnv)
        resolve(config)
      })
    )
    return this._config as BoosterConfig
  }

  async performChecks(): Promise<void> {
    this.logger.info('Checking project structure')
    if (!this._projectDir) {
      this._projectDir = await this.process.cwd()
    }
    await this.isIndexInitializingBooster()
    await this.performVersionCheck()
  }

  private async isIndexInitializingBooster() {
    // Use the absolute path to ensure the imports work
    const projectPath = await this.getAbsoluteProjectDir()

    // Load the tsconfig.json file to get the rootDir
    type TsConfig = { compilerOptions?: { rootDir?: string } }
    const tsConfigContents = await this.dynamicImporter.import<TsConfig>(`${projectPath}/tsconfig.json`)
    const rootDir = tsConfigContents.compilerOptions?.rootDir

    // If it doesn't have a rootDir, throw an error
    if (!rootDir) throw new CliError('ProjectConfigurationError', 'The tsconfig.json file must have a rootDir property')

    // Ensure the index.ts file initializes Booster
    const indexFilePath = path.join(projectPath, rootDir, 'index.ts')
    const indexFileContents = await this.fileSystem.readFileContents(indexFilePath)
    if (!indexFileContents.includes('Booster.start')) {
      throw new CliError(
        'ProjectConfigurationError',
        'The index.ts file must initialize Booster. Please add the line `Booster.start()` to it.'
      )
    }
  }

  private async getAbsoluteProjectDir(): Promise<string> {
    if (this._projectDir) return this._projectDir
    this._projectDir = await this.process.cwd()
    return path.resolve(this._projectDir)
  }

  async getBoosterVersion(): Promise<string> {
    type PackageJson = { dependencies?: { '@boostercloud/framework-core'?: string } }
    const projectPath = await this.getAbsoluteProjectDir()
    const packageJsonContents = await this.dynamicImporter.import<PackageJson>(`${projectPath}/package.json`)
    const version = packageJsonContents.dependencies?.['@boostercloud/framework-core']
    if (!version) {
      throw new CliError(
        'ProjectConfigurationError',
        'The package.json file must have a dependency on @boostercloud/framework-core'
      )
    }
    const versionParts = version
      .replace('workspace:', '') // We remove the workspace protocol in case we're in the Booster monorepo
      .replace('^', '') // We don't care about the caret
      .replace('.tgz', '') // We remove the .tgz extension in case the project is using a local package
      .split('-')
    const result = versionParts.pop()
    if (!result) {
      throw new CliError(
        'ProjectConfigurationError',
        'The version of @boostercloud/framework-core is not valid, found: ' + version
      )
    }
    return result
  }

  private async performVersionCheck() {
    const boosterVersion = await this.getBoosterVersion()
    if (!this.cliVersion) {
      throw new ImpossibleError('The CLI version is not set')
    }
    if (semver.major(boosterVersion) != semver.major(this.cliVersion)) {
      this.logger.warn(
        `WARNING: The CLI version (${this.cliVersion}) and the Booster version used in the project (${boosterVersion}) differ in the major version. This means that there are breaking changes between them, and your project might not work properly. Please check the release notes before continuing.`
      )
    } else if (semver.minor(boosterVersion) != semver.minor(this.cliVersion)) {
      this.logger.warn(
        `WARNING: The CLI version (${this.cliVersion}) and the Booster version used in the project (${boosterVersion}) differ in the minor version. This means that there could be new features in the CLI or the framework that might not be supported by your project. Please check the release notes before continuing.`
      )
    } else if (semver.patch(boosterVersion) != semver.patch(this.cliVersion)) {
      this.logger.warn(
        `WARNING: The CLI version (${this.cliVersion}) and the Booster version used in the project (${boosterVersion}) differ in the patch version. This means that there could be bugs in your project that have been fixed in the framework. Please check the release notes before continuing.`
      )
    }
  }

  private async assertNameIsCorrect(name: string): Promise<void> {
    type StringPredicate = (x: string) => boolean

    // Current characters max length: 37
    // Lambda name limit is 64 characters
    // `-subscriptions-notifier` lambda is 23 characters
    // `-app` prefix is added to application stack
    // which is 64 - 23 - 4 = 37
    const maxProjectNameLength = 37

    const constraints: ReadonlyArray<[StringPredicate, string]> = [
      [(name: string) => name.length > maxProjectNameLength, `be longer than ${maxProjectNameLength} characters`],
      [(name: string) => name.includes(' '), 'contain spaces'],
      [(name: string) => name.toLowerCase() !== name, 'contain uppercase letters'],
      [(name: string) => name.includes('_'), 'contain underscore'],
    ]

    for (const [constraint, restrictionText] of constraints) {
      if (constraint(name)) {
        const message = this.nameFormatErrorMessage(name, restrictionText)
        throw new CliError('CloudProviderError', message)
      }
    }
  }

  private nameFormatErrorMessage(name: string, restrictionText: string): string {
    return `Project name cannot ${restrictionText}:\n\n    Found: '${name}'`
  }
}

/**
 * Helper function to check if the environment was properly configured in the user's config
 */
function checkEnvironmentWasConfigured(app: BoosterApp, currentEnv: string): void {
  if (app.configuredEnvironments.size == 0) {
    throw new Error(
      "You haven't configured any environment. Please make sure you have at least one environment configured by calling 'Booster.configure' method (normally done inside the folder 'src/config')"
    )
  }
  if (!app.configuredEnvironments.has(currentEnv)) {
    throw new Error(
      `The environment '${currentEnv}' does not match any of the environments you used to configure your Booster project, which are: '${Array.from(
        app.configuredEnvironments
      ).join(', ')}'`
    )
  }
}
