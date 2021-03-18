import { Logger } from '@boostercloud/framework-types'
import util = require('util')
import { scopeLogger } from '../helpers/logger'
const exec = util.promisify(require('child_process').exec)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'
  private logger: Logger

  constructor(logger: Logger) {
    this.logger = scopeLogger('HelmManager', logger)
  }

  /**
   * apply a raw helm command. The user does not need to provide the `helm` keyword
   * for example: `helm install randomChart` will be `exec('install randomChart')`
   */
  public exec(args: string): Promise<{ stderr?: string; stdout?: string }> {
    const l = scopeLogger('exec', this.logger)
    const cmd = `${this.BASE_COMMAND} ${args}`
    l.debug('Executing command', cmd)
    return exec(cmd)
  }

  /**
   * check that current installed helm version is greater than 3
   */
  public async isVersion3(): Promise<void> {
    const l = scopeLogger('isVersion3', this.logger)
    l.debug('Getting version using stdout')
    const { stdout } = await this.exec('version')
    if (!stdout) {
      l.debug('No stdout when getting version, throwing')
      throw new Error('Unable to get Helm version, please check your Helm installation')
    }
    l.debug('Checking if version matches properly')
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? null : match[1]
    const cleanedVersion = semver.clean(version)
    l.debug('Got version', cleanedVersion)
    if (!semver.gte(cleanedVersion, '3.0.0')) {
      l.debug('Version is lower, throwing')
      throw new Error('Current Helm version lower than 3.0.0, please update it')
    }
  }

  /**
   * check if a specific repo is already available to be used by helm
   */
  public async isRepoInstalled(repoName: string): Promise<boolean> {
    const l = scopeLogger('isRepoInstalled', this.logger)
    try {
      l.debug('Listing repos')
      const { stdout, stderr } = await this.exec('repo list')
      if (stderr) {
        l.debug('Found stderr, repo not installed')
        return false
      }
      const includesRepoName = !!stdout?.includes(repoName)
      l.debug('Stdout does', includesRepoName ? '' : 'NOT', 'include', repoName)
      return includesRepoName
    } catch (e) {
      l.debug('Process errored with', e, 'returning false')
      return false
    }
  }

  /**
   * install a specific repo to be used by helm
   */
  public async installRepo(repoName: string, repoUrl: string): Promise<void> {
    const l = scopeLogger('installRepo', this.logger)
    l.debug('Adding repo', repoName, 'with URL', repoUrl)
    const install = await this.exec(`repo add ${repoName} ${repoUrl}`)
    if (!install.stdout) {
      l.debug("Didn't find stdout, throwing")
      throw new Error('Unable to install Helm repo, please check your Helm installation')
    }
    l.debug('Updating helm repo')
    await this.updateHelmRepo()
    l.debug('Checking if repo', repoName, 'is installed')
    const repoInstalled = await this.isRepoInstalled(repoName)
    if (!repoInstalled) {
      l.debug('Repo is not installed, throwing')
      throw new Error('Unable to install Helm repo, please check your Helm installation')
    }
  }

  /**
   * update repo definition in helm
   */
  public async updateHelmRepo(): Promise<void> {
    const update = await this.exec('repo update')
    if (!update.stdout) {
      throw new Error('Unable to update Helm repo, please check your Helm installation')
    }
  }
}
