import { HelmManager } from '../../src/infrastructure/helm-manager'
import { replace, fake, restore } from 'sinon'
import { expect } from '../expect'
import { Logger } from '@boostercloud/framework-types'

describe('The user interaction with Helm', async () => {
  const fakeLogger: Logger = {
    info: fake(),
    error: fake(),
    debug: fake(),
  }
  const helm = new HelmManager(fakeLogger)
  afterEach(() => {
    restore()
  })

  it('allows checking helm is installed but it is not installed', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stderr: 'command not found,' }))
    await expect(helm.isVersion3()).eventually.to.be.rejectedWith(
      'Unable to get Helm version, please check your Helm installation'
    )
  })

  it('allows checking helm is installed but but there is a lower version than expected installed', async () => {
    replace(
      HelmManager.prototype,
      'exec',
      fake.resolves({
        stdout:
          'version.BuildInfo{Version:"v2.9.9", GitCommit:"fe51cd1e31e6a202cba7dead9552a6d418ded79a", GitTreeState:"clean", GoVersion:"go1.13.10"}',
      })
    )
    await expect(helm.isVersion3()).to.eventually.be.rejectedWith(
      'Current Helm version lower than 3.0.0, please update it'
    )
  })

  it('allows checking for a non installed repo', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stdout: 'repo1 installed' }))
    const isRepoInstalled = await helm.isRepoInstalled('repoName')
    expect(isRepoInstalled).to.be.false
  })

  it('allows checking for a repo installed but command fails', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stderr: 'random error' }))
    const isRepoInstalled = await helm.isRepoInstalled('repoName')
    expect(isRepoInstalled).to.be.false
  })

  it('allows checking for a repo installed and the repo is already installed', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stdout: 'repoName otherRepo' }))
    const isRepoInstalled = await helm.isRepoInstalled('repoName')
    expect(isRepoInstalled).to.be.true
  })

  it('allows installing a new repo but the installation fails', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stderr: 'random error' }))
    await expect(helm.installRepo('repo', 'url')).to.be.eventually.rejectedWith(
      'Unable to install Helm repo, please check your Helm installation'
    )
  })

  it('allows installing a new repo but we are not able to verify it', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stdout: 'ok' }))
    replace(HelmManager.prototype, 'isRepoInstalled', fake.resolves(false))
    await expect(helm.installRepo('repo', 'url')).to.eventually.be.rejectedWith(
      'Unable to install Helm repo, please check your Helm installation'
    )
  })

  it('allows sucessfully installing the repo', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stdout: 'ok' }))
    replace(HelmManager.prototype, 'isRepoInstalled', fake.resolves(true))
    let error = false
    await helm.installRepo('repo', 'url').catch(() => {
      error = true
    })
    expect(error).to.be.false
  })

  it('allows updating a repo but there is an error', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stderr: 'error updating repo' }))
    await expect(helm.updateHelmRepo()).to.be.eventually.rejectedWith(
      'Unable to update Helm repo, please check your Helm installation'
    )
  })

  it('allows sucessfully update the repo', async () => {
    replace(HelmManager.prototype, 'exec', fake.resolves({ stdout: 'ok' }))
    let error = false
    await helm.updateHelmRepo().catch(() => {
      error = true
    })
    expect(error).to.be.false
  })
})
