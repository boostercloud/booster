import util = require('util')
const exec = util.promisify(require('child_process').exec)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'

  /**
   * apply a raw helm command. The user does not need to provide the `helm` keyword
   * for example: `helm install randomChart` will be `exec('install randomChart')`
   */
  public exec(args: string): Promise<{ stderr?: string; stdout?: string }> {
    return exec(`${this.BASE_COMMAND} ${args}`)
  }

  /**
   * check that current installed helm version is greater than 3
   */
  public async isVersion3(): Promise<void> {
    const { stdout } = await this.exec('version')
    if (!stdout) {
      throw new Error('Unable to get Helm version, please check your Helm installation')
    }
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? null : match[1]
    const cleanedVersion = semver.clean(version)
    if (!semver.gte(cleanedVersion, '3.0.0')) {
      throw new Error('Current Helm version lower than 3.0.0, please update it')
    }
  }

  /**
   * check if a specific repo is already available to be used by helm
   */
  public async isRepoInstalled(repoName: string): Promise<boolean> {
    try {
      const { stdout, stderr } = await this.exec('repo list');
      if (stderr) {
        return false
      }
      return !!stdout?.includes(repoName)
    } catch (e) {
      return false
    }
  }

  /**
   * install a specific repo to be used by helm
   */
  public async installRepo(repoName: string, repoUrl: string): Promise<void> {
    const install = await this.exec(`repo add ${repoName} ${repoUrl}`)
    if (!install.stdout) {
      throw new Error('Unable to install Helm repo, please check your Helm installation')
    }
    await this.updateHelmRepo()
    const repoInstalled = await this.isRepoInstalled(repoName)
    if (!repoInstalled) {
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
