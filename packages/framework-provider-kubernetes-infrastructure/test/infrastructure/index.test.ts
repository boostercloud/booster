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

  it('allows finishing deploy correctly', (done) => {
    const msgArray: string[] = []
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
    deploy(config).subscribe(
      (value) => {
        msgArray.push(value)
      },
      undefined,
      () => {
        expect(msgArray.length).to.be.equal(8)
        expect(msgArray[msgArray.length - 1]).to.be.equal(`Your app is ready in this url: http://${serviceUrl}`)
        done()
      }
    )
  })

  it('allows deploying but the namespace validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the helms validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the volume claim validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the upload service validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but he booster service validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the dapr service validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the eventStore validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.throws(errorMsg))

    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the Upload pod validation fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying but the User code Upload fails', (done) => {
    replace(DeployManager.prototype, 'ensureNamespaceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureHelmIsReady', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureVolumeClaimExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureBoosterServiceExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureDaprExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureEventStoreExists', fake.resolves(true))
    replace(DeployManager.prototype, 'ensureUploadPodExists', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.throws(errorMsg))
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows deploying butthe booster pod validation fails', (done) => {
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
    deploy(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows finishing nuke correctly', (done) => {
    const msgArray: string[] = []
    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteAllResources', fake.resolves(true))
    nuke(config).subscribe(
      (value) => {
        msgArray.push(value)
      },
      undefined,
      () => {
        expect(msgArray.length).to.be.equal(4)
        expect(msgArray[msgArray.length - 1]).to.include('Your app is terminated and destroyed')
        done()
      }
    )
  })

  it('allows nuking but delete dapr fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.throws(errorMsg))
    nuke(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('alows nuking but delete redis fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.throws(errorMsg))

    nuke(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })

  it('allows nuking but delete resources fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteAllResources', fake.throws(errorMsg))

    nuke(config).subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${errorMsg}`)
        done()
      },
      undefined
    )
  })
})
