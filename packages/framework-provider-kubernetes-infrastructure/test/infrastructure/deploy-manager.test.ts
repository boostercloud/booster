import { expect } from '../expect'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { HelmManager } from '../../src/infrastructure/helm-manager'
import { DaprManager } from '../../src/infrastructure/dapr-manager'
import { DeployManager } from '../../src/infrastructure/deploy-manager'
import { stub, restore, replace, fake } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as utils from '../../src/infrastructure/utils'
import { CoreV1Api, KubeConfig, KubernetesObjectApi } from '@kubernetes/client-node'

describe('As part of the deploy the users want to:', async () => {
  const k8sManager = new K8sManagement()
  const configuration = new BoosterConfig('production')
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, k8sManager, helmManager)
  const deployManager = new DeployManager(configuration, k8sManager, daprManager, helmManager)

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
  })

  afterEach(() => {
    restore()
  })

  it('check that helm is ready and it is working', async () => {
    stub(helmManager, 'isVersion3').resolves(true)
    await expect(deployManager.verifyHelm()).to.be.eventually.equal(true)
  })

  it('check that helm is ready and it is not working', async () => {
    const error = 'helm is not ready'
    stub(helmManager, 'isVersion3').rejects(error)
    await expect(deployManager.verifyHelm()).to.be.eventually.rejectedWith(error)
  })

  it('check that Dapr is ready and it is working', async () => {
    stub(helmManager, 'isRepoInstalled').resolves(true)
    stub(k8sManager, 'getPodFromNamespace').resolves(undefined)
    stub(helmManager, 'exec').resolves()
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    await expect(deployManager.verifyDapr()).to.be.eventually.equal(true)
  })

  it('check that Dapr is ready and it fails', async () => {
    const error = 'timeout'
    stub(helmManager, 'isRepoInstalled').resolves(true)
    stub(k8sManager, 'getPodFromNamespace').resolves(undefined)
    stub(helmManager, 'exec').resolves()
    stub(k8sManager, 'waitForPodToBeReady').rejects(error)
    await expect(deployManager.verifyDapr()).to.be.eventually.rejectedWith(error)
  })

  it('check that EventStore is ready and it is working', async () => {
    stub(daprManager, 'configureEventStore').resolves()
    await expect(deployManager.verifyEventStore()).to.be.eventually.equal(true)
  })

  it('check that EventStore is ready and it fails', async () => {
    const error = 'error'
    stub(daprManager, 'configureEventStore').rejects(error)
    await expect(deployManager.verifyEventStore()).to.be.eventually.rejectedWith(error)
  })

  it('check that namespace exists', async () => {
    stub(k8sManager, 'getNamespace').resolves({ name: 'name' })
    await expect(deployManager.verifyNamespace()).to.be.eventually.equal(true)
  })

  it('check that namespace is correctly created', async () => {
    stub(k8sManager, 'getNamespace').resolves(undefined)
    stub(k8sManager, 'createNamespace').resolves(true)
    await expect(deployManager.verifyNamespace()).to.be.eventually.equal(true)
  })

  it('check that namespace is correctly created but it fails', async () => {
    const error = 'Unable to create a namespace for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getNamespace').resolves(undefined)
    stub(k8sManager, 'createNamespace').resolves(false)
    await expect(deployManager.verifyNamespace()).to.be.eventually.rejectedWith(error)
  })

  it('check that volumeClaim is ready', async () => {
    stub(k8sManager, 'getVolumeClaimFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.verifyVolumeClaim()).to.be.eventually.equal(true)
  })

  it('check that volumeClaim is ready but it fails', async () => {
    const error = 'Unable to create a volume claim for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getVolumeClaimFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.verifyVolumeClaim()).to.be.eventually.rejectedWith(error)
  })

  it('check that uploadService is ready', async () => {
    stub(k8sManager, 'getServiceFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.verifyUploadService()).to.be.eventually.equal(true)
  })

  it('check that uploadService is ready but it fails', async () => {
    const error = 'Unable to create fileuploader service for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getServiceFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.verifyUploadService()).to.be.eventually.rejectedWith(error)
  })

  it('check that boosterService is ready', async () => {
    stub(k8sManager, 'getServiceFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.verifyBoosterService()).to.be.eventually.equal(true)
  })

  it('check that boosterService is ready but it fails', async () => {
    const error = 'Unable to create booster service for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getServiceFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.verifyBoosterService()).to.be.eventually.rejectedWith(error)
  })

  it('check that uploadPod is ready', async () => {
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    await expect(deployManager.verifyUploadPod()).to.be.eventually.equal(true)
  })

  it('check that uploadPod is ready but it fails', async () => {
    const error = 'timeout'
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').rejects(error)
    await expect(deployManager.verifyUploadPod()).to.be.eventually.rejectedWith(error)
  })

  it('check that boosterPod is ready', async () => {
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    stub(k8sManager, 'applyTemplate').resolves([{ apiVersion: '1' }])
    await expect(deployManager.verifyBoosterPod()).to.be.eventually.equal(true)
  })

  it('check that boosterPod is ready but it fails', async () => {
    const error = 'error'
    stub(k8sManager, 'getPodFromNamespace').rejects(error)
    await expect(deployManager.verifyBoosterPod()).to.be.eventually.rejectedWith(error)
  })

  it('check that the upload code works', async () => {
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    replace(utils, 'createIndexFile', fake.resolves(''))
    replace(utils, 'uploadFile', fake.resolves({ statusCode: 200 }))
    await expect(deployManager.uploadUserCode()).to.be.eventually.equal(true)
  })
})
