/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/k8s-management'
import { HelmManager } from '../../src/infrastructure/helm-manager'
import { DaprManager } from '../../src/infrastructure/dapr-manager'
import { stub, restore, replace, fake } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'
import { stateStore } from '../../src/infrastructure/templates/statestore'
import { internet } from 'faker'
import { CoreV1Api, KubeConfig, KubernetesObjectApi } from '@kubernetes/client-node'
const fs = require('fs')

describe('Users Dapr interaction inside the cluster', () => {
  replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()) as any)
  replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
  const config = new BoosterConfig('test')
  const k8sManager = new K8sManagement(config)
  const helmManager = new HelmManager(config)
  const daprManager = new DaprManager(config, k8sManager, helmManager)

  afterEach(() => {
    restore()
  })

  it('allows configuring the eventStore', async () => {
    replace(fs, 'existsSync', fake.returns(false))
    stub(daprManager, 'ensureEventStoreIsReady').resolves({
      namespace: internet.domainWord(),
      eventStoreHost: internet.url(),
      eventStoreUsername: internet.userName(),
      eventStorePassword: internet.password(),
    })
    stub(daprManager, 'getEventStorePassword').resolves('awesomePass')
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').resolves({ stdout: '' })
    await expect(daprManager.configureEventStore()).to.eventually.be.fulfilled
  })

  it('allows configuring the eventStore but the cluster fails', async () => {
    replace(fs, 'existsSync', fake.returns(false))
    stub(daprManager, 'ensureEventStoreIsReady').resolves({
      namespace: internet.domainWord(),
      eventStoreHost: internet.url(),
      eventStoreUsername: internet.userName(),
      eventStorePassword: internet.password(),
    })
    stub(daprManager, 'getEventStorePassword').resolves('awesomePass')
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').resolves({ stderr: 'error!!' })
    await expect(daprManager.configureEventStore()).eventually.to.be.rejectedWith('error!!')
  })

  it('allows deleting Dapr service', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).to.eventually.be.fulfilled
  })

  it('allows deleting Dapr service but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).eventually.to.be.rejectedWith('error!!')
  })

  it('allows deleting Event Store', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).to.eventually.be.fulfilled
  })

  it('allows deleting Event Store but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).eventually.to.be.rejectedWith('error!!')
  })
})
