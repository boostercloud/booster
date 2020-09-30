import { expect } from '../expect'
import { deploy, nuke } from '../../src/infrastructure/index'
import { BoosterConfig } from '@boostercloud/framework-types'
import { restore, replace, fake } from 'sinon'
import { CoreV1Api, KubeConfig, KubernetesObjectApi } from '@kubernetes/client-node'
import { DeployManager } from '../../src/infrastructure/deploy-manager'
import { internet } from 'faker'

describe('During the deploy or nuke of Booster apps:', async () => {
  const config = new BoosterConfig('production')
  const errorMsg = 'error!'

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
  })

  afterEach(() => {
    restore()
  })

  it('allows finishing deploy correctly', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    const serviceUrl = internet.ip
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.resolves(true))
    replace(DeployManager.prototype, 'deployBoosterApp', fake.resolves(serviceUrl))
    await deploy(config, logger)

    expect(logger.info.getCalls().length).to.be.equal(8)
    expect(logger.info).to.have.been.calledWith(`Your app is ready in this url: http://${serviceUrl}`)
  })

  it('allows deploying but the namespace validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.throws(errorMsg))
    await expect(deploy(config, logger)).to.eventually.be.rejectedWith(errorMsg)
  })

  it('allows deploying but the helms validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.eventually.be.rejectedWith(errorMsg)
  })

  it('allows deploying but the volume claim validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but the upload service validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but he booster service validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but the dapr service validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but the eventStore validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but the Upload pod validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying but the User code Upload fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows deploying butthe booster pod validation fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.resolves(true))
    replace(DeployManager.prototype, 'deployBoosterApp', fake.throws(errorMsg))

    await expect(deploy(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows finishing nuke correctly', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteAllResources', fake.resolves(true))

    await nuke(config, logger)

    expect(logger.info.getCalls().length).to.be.equal(4)
    expect(logger.info).to.have.been.calledWithMatch(/Your app is terminated and destroyed/)
  })

  it('allows nuking but delete dapr fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'deleteDapr', fake.throws(errorMsg))

    await expect(nuke(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows nuking but delete redis fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.throws(errorMsg))

    await expect(nuke(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })

  it('allows nuking but delete resources fails', async () => {
    const logger = {
      info: fake(),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any

    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteAllResources', fake.throws(errorMsg))

    await expect(nuke(config, logger)).to.be.eventually.rejectedWith(errorMsg)
  })
})
