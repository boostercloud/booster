import { expect } from '../expect'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/k8s-management'
import { HelmManager } from '../../src/infrastructure/helm-manager'
import { DaprManager } from '../../src/infrastructure/dapr-manager'
import { DeployManager } from '../../src/infrastructure/deploy-manager'
import { stub, restore, replace, fake } from 'sinon'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import * as utils from '../../src/infrastructure/utils'
import { CoreV1Api, KubeConfig, KubernetesObjectApi } from '@kubernetes/client-node'

describe('User interaction during the deploy:', async () => {
  const fakeLogger: Logger = {
    info: fake(),
    error: fake(),
    debug: fake(),
  }
  const k8sManager = new K8sManagement(fakeLogger)
  const configuration = new BoosterConfig('production')
  const helmManager = new HelmManager(fakeLogger)
  const daprManager = new DaprManager(fakeLogger, configuration, k8sManager, helmManager)
  const deployManager = new DeployManager(fakeLogger, configuration, k8sManager, daprManager, helmManager)

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
  })

  afterEach(() => {
    restore()
  })

  it('allows verifying that helm is ready and it is working', async () => {
    stub(helmManager, 'isVersion3').resolves()
    await expect(deployManager.ensureHelmIsReady()).to.eventually.be.fulfilled
  })

  it('allows verifying helm is ready and it is not working', async () => {
    const error = new Error('helm is not ready')
    stub(helmManager, 'isVersion3').throws(error)
    await expect(deployManager.ensureHelmIsReady()).to.eventually.be.rejectedWith(error)
  })

  it('allows verifying that Dapr is ready and it is working', async () => {
    stub(helmManager, 'isRepoInstalled').resolves(true)
    stub(k8sManager, 'getPodFromNamespace').resolves(undefined)
    stub(helmManager, 'exec').resolves()
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    await expect(deployManager.ensureDaprExists()).to.eventually.be.fulfilled
  })

  it('allows checking if Dapr is ready but it is failing', async () => {
    const error = new Error('timeout')
    stub(helmManager, 'isRepoInstalled').resolves(true)
    stub(k8sManager, 'getPodFromNamespace').resolves(undefined)
    stub(helmManager, 'exec').resolves()
    stub(k8sManager, 'waitForPodToBeReady').throws(error)
    await expect(deployManager.ensureDaprExists()).to.eventually.be.rejectedWith(error)
  })

  it('allows verifying that EventStore is ready and it is working', async () => {
    stub(daprManager, 'configureEventStore').resolves()
    await expect(deployManager.ensureEventStoreExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that EventStore is ready and it fails', async () => {
    const error = new Error('error')
    stub(daprManager, 'configureEventStore').throws(error)
    await expect(deployManager.ensureEventStoreExists()).to.eventually.be.rejectedWith(error)
  })

  it('allows verifying that namespace exists', async () => {
    stub(k8sManager, 'getNamespace').resolves({ name: 'name' })
    await expect(deployManager.ensureNamespaceExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that namespace is correctly created', async () => {
    stub(k8sManager, 'getNamespace').resolves(undefined)
    stub(k8sManager, 'createNamespace').resolves(true)
    await expect(deployManager.ensureNamespaceExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that namespace is correctly created but it fails', async () => {
    const error = 'Unable to create a namespace for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getNamespace').resolves(undefined)
    stub(k8sManager, 'createNamespace').resolves(false)
    await expect(deployManager.ensureNamespaceExists()).to.be.eventually.rejectedWith(error)
  })

  it('allows verifying that volumeClaim is ready', async () => {
    stub(k8sManager, 'getVolumeClaimFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.ensureVolumeClaimExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that volumeClaim is ready but it fails', async () => {
    const error = 'Unable to create a volume claim for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getVolumeClaimFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.ensureVolumeClaimExists()).to.be.eventually.rejectedWith(error)
  })

  it('allows verifying that uploadService is ready', async () => {
    stub(k8sManager, 'getServiceFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.ensureUploadServiceExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that uploadService is ready but it fails', async () => {
    const error = 'Unable to create fileuploader service for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getServiceFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.ensureUploadServiceExists()).to.be.eventually.rejectedWith(error)
  })

  it('allows verifying that boosterService is ready', async () => {
    stub(k8sManager, 'getServiceFromNamespace').resolves({ name: 'name' })
    await expect(deployManager.ensureBoosterServiceExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that boosterService is ready but it fails', async () => {
    const error = 'Unable to create booster service for your project, please check your Kubectl configuration'
    stub(k8sManager, 'getServiceFromNamespace').resolves(undefined)
    stub(k8sManager, 'applyTemplate').resolves([])
    await expect(deployManager.ensureBoosterServiceExists()).to.be.eventually.rejectedWith(error)
  })

  it('allows verifying that uploadPod is ready', async () => {
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    await expect(deployManager.ensureUploadPodExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that uploadPod is ready but it fails', async () => {
    const error = 'timeout'
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').throws(error)
    await expect(deployManager.ensureUploadPodExists()).to.eventually.be.rejected
  })

  it('allows verifying that boosterPod is ready', async () => {
    stub(k8sManager, 'getPodFromNamespace').resolves({ name: 'name' })
    stub(k8sManager, 'waitForPodToBeReady').resolves()
    stub(k8sManager, 'applyTemplate').resolves([{ apiVersion: '1' }])
    await expect(deployManager.ensureBoosterPodExists()).to.eventually.be.fulfilled
  })

  it('allows verifying that boosterPod is ready but it fails', async () => {
    const error = 'error'
    stub(k8sManager, 'getPodFromNamespace').throws(error)
    await expect(deployManager.ensureBoosterPodExists()).to.eventually.be.rejected
  })

  it('allows verifying that the upload code works', async () => {
    stub(k8sManager, 'waitForServiceToBeReady').resolves()
    replace(utils, 'uploadFile', fake.resolves({ statusCode: 200 }))
    replace(utils, 'createProjectZipFile', fake.resolves('path'))
    await expect(deployManager.uploadUserCode()).to.eventually.be.fulfilled
  })
})
