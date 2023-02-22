import { FileSystem } from '../file-system'
import { Process } from '../process'
import { PackageManager } from '.'
import { CliError } from '../../common/errors'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'

@Component
export class RushPackageManager implements PackageManager {
  private _projectRoot?: string

  constructor(readonly logger: Logger, readonly process: Process, readonly fileSystem: FileSystem) {}

  getLockfileName(): string {
    return 'common/config/rush/pnpm-lock.yaml'
  }

  async getProjectRoot(): Promise<string> {
    return this._projectRoot ? this._projectRoot : this.process.cwd()
  }

  async setProjectRoot(projectRoot: string): Promise<void> {
    this._projectRoot = projectRoot || (await this.process.cwd())
  }

  // TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rushx.js
  private async runRushX(scriptName: string, args: readonly string[]): Promise<string> {
    const scriptExists = await this.checkScriptExists(scriptName)
    if (!scriptExists) {
      throw new CliError('PackageManagerError', `There is no script named ${scriptName} in the package.json file`)
    }
    const rootDir = await this.getProjectRoot()
    const command = `rushx ${scriptName} ${args.join(' ')}`.trim()
    try {
      return this.process.exec(command, rootDir)
    } catch (e) {
      if (e instanceof CliError) throw e
      throw new CliError('PackageManagerError', `There were some issues running script ${scriptName}: ${e}`, e)
    }
  }

  // TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rush.js
  private async runRush(scriptName: string, args: readonly string[]): Promise<string> {
    const rootDir = await this.getProjectRoot()
    const command = `rush ${scriptName} ${args.join(' ')}`.trim()
    try {
      return this.process.exec(command, rootDir)
    } catch (e) {
      if (e instanceof CliError) throw e
      throw new CliError('PackageManagerError', `There were some issues running script ${scriptName}: ${e}`, e)
    }
  }

  async runScript(scriptName: string, args: readonly string[]): Promise<string> {
    return this.runRushX(scriptName, args)
  }

  async installProductionDependencies(): Promise<void> {
    throw new CliError('PackageManagerError', 'installProductionDependencies is not supported by Rush')
  }

  async installAllDependencies(): Promise<void> {
    await this.runRush('update', [])
    await this.runRush('install', [])
  }

  async build(args: readonly string[]): Promise<string> {
    return this.runRush('build', args)
  }

  private async checkScriptExists(scriptName: string): Promise<boolean> {
    const projectRoot = await this.getProjectRoot()
    const packageJson = await this.fileSystem.readFileContents(`${projectRoot}/package.json`)
    const packageJsonContents = JSON.parse(packageJson)
    return packageJsonContents.scripts && packageJsonContents.scripts[scriptName]
  }
}
