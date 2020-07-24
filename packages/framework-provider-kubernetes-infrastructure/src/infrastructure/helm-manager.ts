/* eslint-disable @typescript-eslint/explicit-function-return-type */
import util = require('util')
const exec = util.promisify(require('child_process').exec)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'

  public exec(args: string): Promise<{ stderr?: string; stdout?: string }> {
    return exec(`${this.BASE_COMMAND} ${args}`)
  }

  public async isVersion3(): Promise<boolean> {
    const { stdout } = await this.exec('version')
    if (!stdout) {
      return Promise.reject('Unable to get Helm version, please check your Helm installation')
    }
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? null : match[1]
    const cleanVersion = semver.clean(version)
    if (!semver.gte(cleanVersion, '3.0.0')) {
      return Promise.reject('Current Helm version lower than 3.0.0, please update it')
    }
    return true
  }

  public async isRepoInstalled(repoName: string): Promise<boolean> {
    const listRepo = await this.exec('repo list')
    if (!listRepo.stdout) {
      return false
    }
    if (!listRepo.stdout.includes(repoName)) {
      return false
    }
    return true
  }

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

  public async updateHelmRepo(): Promise<void> {
    const update = await this.exec('repo update')
    if (!update.stdout) {
      return Promise.reject('Unable to update Helm repo, please check your Helm installation')
    }
    return Promise.resolve()
  }
}
