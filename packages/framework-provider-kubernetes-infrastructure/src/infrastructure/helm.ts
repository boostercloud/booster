import { exec, ChildProcessPromise, PromiseResult } from 'child-process-promise'
const semver = require('semver')

export class HelmManagement {
  private BASE_COMMAND = 'helm'
  private BOOSTER_HELM_REPO = 'http://booster.helm.s3-website-eu-west-1.amazonaws.com'
  private helmError = ''
  private helmReady = false

  public async init(): Promise<void> {
    this.helmReady = await this.isHelmReadyToDeploy()
  }

  public exec(args: string): ChildProcessPromise<PromiseResult<string>> {
    return exec(`${this.BASE_COMMAND} ${args}`)
  }

  public async getVersion(): Promise<string | null> {
    const helmVersion = await this.exec('version')
    if (!helmVersion.stdout) {
      return null
    }
    const match = helmVersion.stdout.match(/Version:"(.*?)"/)
    return !match ? null : match[1]
  }

  public isHelmReady(): boolean {
    return this.helmReady
  }

  public getHelmError(): string {
    return this.helmError
  }

  private async boosterRepoInstalled(): Promise<boolean> {
    const listRepo = await this.exec('repo list')
    if (!listRepo.stdout) {
      this.helmError = 'Unable to get the Helm repo list'
      return false
    }
    if (!listRepo.stdout.includes('boosterchart')) {
      this.helmError = 'Booster Helm repo not found'
      return false
    }
    return true
  }

  private async installBoosterRepo(): Promise<boolean> {
    const install = await this.exec(`repo add boosterchart ${this.BOOSTER_HELM_REPO}`)
    if (!install.stdout) {
      return false
    }
    return true
  }

  public async updateHelmRepo(): Promise<boolean> {
    const update = await this.exec('repo update')
    if (!update.stdout) {
      return false
    }
    return true
  }

  public async isBoosterRepoReady(): Promise<boolean> {
    const isBoosterInstalled = await this.boosterRepoInstalled()
    if (!isBoosterInstalled) {
      const installRepo = await this.installBoosterRepo()
      if (!installRepo) {
        return false
      }
    }
    const updateRepos = await this.updateHelmRepo()
    if (!updateRepos) {
      return false
    }
    return true
  }

  private async isHelmReadyToDeploy(): Promise<boolean> {
    //check that helm is installed on client side
    const helmVersion = await this.getVersion()
    if (!helmVersion) {
      this.helmError = 'Helm installation not found'
      return false
    }
    //check that we are using helm 3
    const cleanVersion = semver.clean(helmVersion)
    if (!semver.gte(cleanVersion, '3.0.0')) {
      this.helmError = 'Current Helm version lower than 3.0.0'
      return false
    }
    //check that the booster helm repo is ready and updated
    const repoReady = await this.isBoosterRepoReady()
    if (!repoReady) {
      this.helmError = 'Unable to install the Booster repo in Helm'
      return false
    }
    return true
  }
}
