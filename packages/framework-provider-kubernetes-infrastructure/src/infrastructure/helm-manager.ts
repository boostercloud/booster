import util = require('util')
const exec = util.promisify(require('child_process').exec)
const semver = require('semver')

export class HelmManager {
  private BASE_COMMAND = 'helm'

  public exec(args: string): Promise<any> {
    return exec(`${this.BASE_COMMAND} ${args}`)
  }

  // eslint-disable-next-line @typescript-eslint/explicit-function-return-type
  public async isVersion3() {
    const { stdout } = await this.exec('version')
    if (!stdout) {
      throw new Error('Unable to get Helm version, please check your Helm installation')
    }
    const match = stdout.match(/Version:"(.*?)"/)
    const version = !match ? null : match[1]
    const cleanVersion = semver.clean(version)
    if (!semver.gte(cleanVersion, '3.0.0')) {
      throw new Error('Current Helm version lower than 3.0.0, please update it')
    }
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

  // eslint-disable-next-line @typescript-eslint/explicit-function-return-type
  public async installRepo(repoName: string, repoUrl: string) {
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

  // eslint-disable-next-line @typescript-eslint/explicit-function-return-type
  public async updateHelmRepo() {
    const update = await this.exec('repo update')
    if (!update.stdout) {
      throw new Error('Unable to update Helm repo, please check your Helm installation')
    }
  }
}
