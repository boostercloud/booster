import { PackageManager } from '.'
import { Process } from '../process'
import { FileSystem } from '../file-system'
import { CliError } from '../../common/errors'
import { Logger } from '@boostercloud/framework-types'

/**
 * A common implementation of the PackageManager interface that can be used
 * to implement simple package managers that do not require monorepo support.
 */
export abstract class SimplePackageManager implements PackageManager {
  private _projectRoot?: string

  constructor(
    readonly packageManagerCommand: string,
    readonly logger: Logger,
    readonly process: Process,
    readonly fileSystem: FileSystem
  ) {}

  async catch(e: unknown): Promise<CliError> {
    if (e instanceof CliError) return e
    return new CliError('PackageManagerError', 'An unknown error occurred', e)
  }

  getLockfileName(): string {
    return 'package-lock.json'
  }

  async getProjectRoot(): Promise<string> {
    return this._projectRoot ? this._projectRoot : this.process.cwd()
  }

  async setProjectRoot(projectRoot: string): Promise<void> {
    this._projectRoot = projectRoot || (await this.process.cwd())
  }

  async runScript(scriptName: string, args: readonly string[]): Promise<string> {
    const scriptExists = await this.checkScriptExists(scriptName)
    if (!scriptExists) {
      throw new CliError('PackageManagerError', `There is no script named ${scriptName} in the package.json file`)
    }
    const rootDir = await this.getProjectRoot()
    const command = `${this.packageManagerCommand} run ${scriptName} ${args.join(' ')}`.trim()
    try {
      return this.process.exec(command, rootDir)
    } catch (e) {
      if (e instanceof CliError) throw e
      throw new CliError('PackageManagerError', `There were some issues running script ${scriptName}: ${e}`, e)
    }
  }

  async installProductionDependencies(): Promise<void> {
    try {
      await this.runScript('install', ['--production', '--no-bin-links', '--no-optional'])
    } catch (e) {
      if (e instanceof CliError) throw e
      throw new CliError('ProcessError', `There were some issues installing prod dependencies: ${e}`, e)
    }
  }

  async installAllDependencies(): Promise<void> {
    try {
      await this.runScript('install', [])
    } catch (e) {
      if (e instanceof CliError) throw e
      throw new CliError('ProcessError', `There were some issues installing all dependencies: ${e}`, e)
    }
  }

  async build(args: readonly string[]): Promise<string> {
    const scriptName = (await this.checkScriptExists('compile')) ? 'compile' : 'build'
    return this.runScript(scriptName, args)
  }

  private async checkScriptExists(scriptName: string): Promise<boolean> {
    const projectRoot = await this.getProjectRoot()
    const packageJson = await this.fileSystem.readFileContents(`${projectRoot}/package.json`)
    const packageJsonContents = JSON.parse(packageJson)
    return packageJsonContents.scripts && packageJsonContents.scripts[scriptName]
  }
}
