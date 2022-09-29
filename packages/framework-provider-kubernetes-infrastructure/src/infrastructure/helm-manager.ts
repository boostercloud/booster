import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import util = require('util')
const execFile = util.promisify(require('child_process').execFile)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'

  constructor(readonly config: BoosterConfig) {}

  /**
   * apply a raw helm command. The user does not need to provide the `helm` keyword
   * for example: `helm install randomChart` will be `exec('install randomChart')`
   */
  public exec(args: string): Promise<{ stderr?: string; stdout?: string }> {
    const logger = getLogger(this.config, 'HelmManager#exec')
    const cmd = `${this.BASE_COMMAND} ${args}`
    logger.debug('Executing command', cmd)
    return execFile(this.BASE_COMMAND, args.split(' '))
  }

  /**
   * check that current installed helm version is greater than 3
   */
  public async isVersion3(): Promise<void> {
    const logger = getLogger(this.config, 'HelmManager#isVersion3')
    logger.debug('Getting version using stdout')
    const { stdout } = await this.exec('version')
    if (!stdout) {
      logger.debug('No stdout when getting version, throwing')
      throw new Error('Unable to get Helm version, please check your Helm installation')
    }
    logger.debug('Checking if version matches properly')
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? undefined : match[1]
    const cleanedVersion = semver.clean(version)
    logger.debug('Got version', cleanedVersion)
    if (!semver.gte(cleanedVersion, '3.0.0')) {
      logger.debug('Version is lower, throwing')
      throw new Error('Current Helm version lower than 3.0.0, please update it')
    }
  }

  /**
   * check if a specific repo is already available to be used by helm
   */
  public async isRepoInstalled(repoName: string): Promise<boolean> {
    const logger = getLogger(this.config, 'HelmManager#isRepoInstalled')
    try {
      logger.debug('Listing repos')
      const { stdout, stderr } = await this.exec('repo list')
      // Only catch errors, not warnings
      if (stderr && stderr.includes('Error')) {
        logger.debug('Found stderr, repo not installed')
        return false
      }
      const includesRepoName = !!stdout?.includes(repoName)
      logger.debug('Stdout does', includesRepoName ? '' : 'NOT', 'include', repoName)
      return includesRepoName
    } catch (e) {
      logger.debug('Process errored with', e, 'returning false')
      return false
    }
  }

  /**
   * install a specific repo to be used by helm
   */
  public async installRepo(repoName: string, repoUrl: string): Promise<void> {
    const logger = getLogger(this.config, 'HelmManager#installRepo')
    logger.debug('Adding repo', repoName, 'with URL', repoUrl)
    const install = await this.exec(`repo add ${repoName} ${repoUrl}`)
    if (!install.stdout) {
      logger.debug("Didn't find stdout, throwing")
      throw new Error('Unable to install Helm repo, please check your Helm installation')
    }
    logger.debug('Updating helm repo')
    await this.updateHelmRepo()
    logger.debug('Checking if repo', repoName, 'is installed')
    const repoInstalled = await this.isRepoInstalled(repoName)
    if (!repoInstalled) {
      logger.debug('Repo is not installed, throwing')
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
