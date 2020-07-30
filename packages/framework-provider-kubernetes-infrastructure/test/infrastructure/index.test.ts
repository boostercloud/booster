import { expect } from '../expect'
import { deploy, nuke } from '../../src/infrastructure/index'
import { BoosterConfig } from '@boostercloud/framework-types'
import { restore, replace, fake } from 'sinon'
import { CoreV1Api, KubeConfig, KubernetesObjectApi } from '@kubernetes/client-node'
import { DeployManager } from '../../src/infrastructure/deploy-manager'
import { internet } from 'faker'

describe('During the deploy or nuke of Booster apps:', async () => {
  const config = new BoosterConfig('production')

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
  })

  afterEach(() => {
    restore()
  })

  it('the deploy finishes correctly', (done) => {
    const msgArray: string[] = []
    const serviceUrl = internet.ip
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyEventStore', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadPod', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.resolves(true))
    replace(DeployManager.prototype, 'deployBoosterApp', fake.resolves(serviceUrl))
    deploy(config).subscribe(
      (value) => {
        msgArray.push(value)
      },
      undefined,
      () => {
        expect(msgArray.length).to.be.equal(9)
        expect(msgArray[msgArray.length - 1]).to.be.equal(`Your app is ready in this url: http://${serviceUrl}`)
        done()
      }
    )
  })

  it('the namespace validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the helms validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the volume claim validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the upload service validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the booster service validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the dapr service validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the eventStore validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyEventStore', fake.rejects('error'))

    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the Upload pod validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyEventStore', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadPod', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the User code Upload fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyEventStore', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadPod', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the booster pod validation fails', (done) => {
    replace(DeployManager.prototype, 'verifyNamespace', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyHelm', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyVolumeClaim', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyBoosterService', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyEventStore', fake.resolves(true))
    replace(DeployManager.prototype, 'verifyUploadPod', fake.resolves(true))
    replace(DeployManager.prototype, 'uploadUserCode', fake.resolves(true))
    replace(DeployManager.prototype, 'deployBoosterApp', fake.rejects('error'))
    deploy(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('the nuke finish correctly', (done) => {
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

  it('delete dapr fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.rejects('error'))
    nuke(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('delete redis fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.rejects('error'))

    nuke(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })

  it('delete resources fails', (done) => {
    replace(DeployManager.prototype, 'deleteDapr', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteRedis', fake.resolves(true))
    replace(DeployManager.prototype, 'deleteAllResources', fake.rejects('error'))

    nuke(config).subscribe(
      () => {},
      (error) => {
        expect(error.toString()).to.be.equal('Error: Error: error')
        done()
      },
      undefined
    )
  })
})
