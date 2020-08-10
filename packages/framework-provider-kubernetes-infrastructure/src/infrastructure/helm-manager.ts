import util = require('util')
const exec = util.promisify(require('child_process').exec)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'

  /**
   * apply a raw helm command. The user does not need to provide the `helm` keyword
   * for example: `helm install randomChart` will be `exec('install randomChart')`
   * @param {string} args
   * @returns {Promise<{ stderr?: string; stdout?: string }>}
   * @memberof HelmManager
   */
  public exec(args: string): Promise<{ stderr?: string; stdout?: string }> {
    return exec(`${this.BASE_COMMAND} ${args}`)
  }

  /**
   * check that current installed helm version is greater than 3
   *
   * @returns {Promise<boolean>}
   * @memberof HelmManager
   */
  public async isVersion3(): Promise<boolean> {
    const { stdout } = await this.exec('version')
    if (!stdout) {
      return Promise.reject('Unable to get Helm version, please check your Helm installation')
    }
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? null : match[1]
    const cleanedVersion = semver.clean(version)
    if (!semver.gte(cleanedVersion, '3.0.0')) {
      return Promise.reject('Current Helm version lower than 3.0.0, please update it')
    }
    return true
  }

  /**
   * check if a specific repo is already available to be used by helm
   *
   * @param {string} repoName
   * @returns {Promise<boolean>}
   * @memberof HelmManager
   */
  public async isRepoInstalled(repoName: string): Promise<boolean> {
    const { stdout } = await this.exec('repo list')
    return !!stdout?.includes(repoName)
  }

  /**
   * install a specific repo to be used by helm
   *
   * @param {string} repoName
   * @param {string} repoUrl
   * @returns {Promise<void>}
   * @memberof HelmManager
   */
  public async installRepo(repoName: string, repoUrl: string): Promise<void> {
    const install = await this.exec(`repo add ${repoName} ${repoUrl}`)
    if (!install.stdout) {
      return Promise.reject('Unable to install Helm repo, please check your Helm installation')
    }
    await this.updateHelmRepo()
    const repoInstalled = await this.isRepoInstalled(repoName)
    if (!repoInstalled) {
      return Promise.reject('Unable to install Helm repo, please check your Helm installation')
    }
    return Promise.resolve()
  }

  /**
   * update repo definition in helm
   *
   * @returns {Promise<void>}
   * @memberof HelmManager
   */
  public async updateHelmRepo(): Promise<void> {
    const update = await this.exec('repo update')
    if (!update.stdout) {
      return Promise.reject('Unable to update Helm repo, please check your Helm installation')
    }
    return Promise.resolve()
  }
}
