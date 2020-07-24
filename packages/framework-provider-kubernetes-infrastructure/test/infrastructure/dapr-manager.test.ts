import { expect } from '../expect'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { HelmManager } from '../../src/infrastructure/helm-manager'
import { DaprManager } from '../../src/infrastructure/dapr-manager'
import { stub, restore } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'
import { stateStore } from '../../src/infrastructure/templates/statestore'

describe('Users want to manage Dapr inside the cluster', () => {
  const k8sManager = new K8sManagement()
  const configuration = new BoosterConfig('test')
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, k8sManager, helmManager)

  afterEach(() => {
    restore()
  })

  it('they want to configure the eventStore', async () => {
    stub(daprManager, 'existsComponentFolder').resolves(false)
    stub(daprManager, 'verifyEventStore').resolves({
      namespace: 'test',
      eventStoreHost: 'test',
      eventStoreUsername: 'test',
      eventStorePassword: 'test',
    })
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').resolves('')
    const result = await daprManager.configureEventStore()
    expect(result.length).to.be.equal(0)
  })

  it('they want to configure the eventStore but the cluster fails', async () => {
    stub(daprManager, 'existsComponentFolder').resolves(false)
    stub(daprManager, 'verifyEventStore').resolves({
      namespace: 'test',
      eventStoreHost: 'test',
      eventStoreUsername: 'test',
      eventStorePassword: 'test',
    })
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').rejects('error!!')
    const result = await daprManager.configureEventStore()
    expect(result.length).to.be.equal(1)
  })

  it('they want to delete Dapr service', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).eventually.to.be.equal('ok')
  })

  it('they want to delete Dapr service but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).eventually.to.be.rejectedWith('error!!')
  })

  it('they want to delete Event Store', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).eventually.to.be.equal('ok')
  })

  it('they want to delete Event Store but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).eventually.to.be.rejectedWith('error!!')
  })
})
