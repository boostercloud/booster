import { HelmManagement } from '../../src/infrastructure/helm'
import * as exec from 'child-process-promise'
import { replace, fake, restore } from 'sinon'
import { expect } from '../expect'

describe('The user wants to get the Helm version', async () => {
  const helm = new HelmManagement()
  await helm.init()

  afterEach(() => {
    restore()
  })

  it('but Helm is not installed', async () => {
    replace(exec, 'exec', fake.resolves({ stderr: 'command not found,' }))
    const helmResponse = await helm.getVersion()
    expect(helmResponse).to.be.null
  })

  it('Helm is installed and it returns the current installed version', async () => {
    replace(
      exec,
      'exec',
      fake.resolves({
        stdout:
          'version.BuildInfo{Version:"v2.9.9", GitCommit:"fe51cd1e31e6a202cba7dead9552a6d418ded79a", GitTreeState:"clean", GoVersion:"go1.13.10"}',
      })
    )

    const helmResponse = await helm.getVersion()
    expect(helmResponse).to.be.equal('v2.9.9')
  })
})

describe('The user wants to use Helm', () => {
  afterEach(() => {
    restore()
  })

  it('but helm is not installed', async () => {
    replace(exec, 'exec', fake.resolves({ stderr: 'command not found,' }))
    const helm = new HelmManagement()
    await helm.init()
    expect(helm.isHelmReady()).to.be.false
    expect(helm.getHelmError()).to.be.equal('Helm installation not found')
  })

  it('but helm has an old version', async () => {
    replace(HelmManagement.prototype, 'getVersion', fake.resolves('v2.9.9'))
    const helm = new HelmManagement()
    await helm.init()
    expect(helm.isHelmReady()).to.be.false
    expect(helm.getHelmError()).to.be.equal('Current Helm version lower than 3.0.0')
  })

  it('but repo is not ready', async () => {
    replace(HelmManagement.prototype, 'getVersion', fake.resolves('v3.0.0'))
    replace(HelmManagement.prototype, 'isBoosterRepoReady', fake.returns(false))
    const helm = new HelmManagement()
    await helm.init()
    expect(helm.isHelmReady()).to.be.false
    expect(helm.getHelmError()).to.be.equal('Unable to install the Booster repo in Helm')
  })

  it('helm is ready', async () => {
    replace(HelmManagement.prototype, 'getVersion', fake.resolves('v3.0.0'))
    replace(HelmManagement.prototype, 'isBoosterRepoReady', fake.returns(true))
    const helm = new HelmManagement()
    await helm.init()
    expect(helm.isHelmReady()).to.be.true
    expect(helm.getHelmError()).to.be.equal('')
  })
})
