import { expect } from '../expect'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { HelmManager } from '../../src/infrastructure/helm-manager'
import { DaprManager } from '../../src/infrastructure/dapr-manager'
import { stub, restore, replace, fake } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'
import { stateStore } from '../../src/infrastructure/templates/statestore'
import { lorem } from 'faker'
const fs = require('fs')

describe('Users Dapr interaction inside the cluster', () => {
  const k8sManager = new K8sManagement()
  const configuration = new BoosterConfig(lorem.word())
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, k8sManager, helmManager)

  afterEach(() => {
    restore()
  })

  it('allows configuring the eventStore', async () => {
    replace(fs, 'existsSync', fake.returns(false))
    stub(daprManager, 'verifyEventStore').resolves({
      namespace: lorem.word(),
      eventStoreHost: lorem.word(),
      eventStoreUsername: lorem.word(),
      eventStorePassword: lorem.word(),
    })
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').resolves('')
    const result = await daprManager.configureEventStore()
    expect(result.length).to.be.equal(0)
  })

  it('allows configuring the eventStore but the cluster fails', async () => {
    replace(fs, 'existsSync', fake.returns(false))
    stub(daprManager, 'verifyEventStore').resolves({
      namespace: lorem.word(),
      eventStoreHost: lorem.word(),
      eventStoreUsername: lorem.word(),
      eventStorePassword: lorem.word(),
    })
    stub(daprManager, 'createDaprComponentFile').resolves
    stub(daprManager, 'readDaprComponentDirectory').resolves(['statestore.yaml'])
    stub(k8sManager, 'execRawCommand').rejects('error!!')
    await expect(daprManager.configureEventStore()).eventually.to.be.rejectedWith('error!!')
  })

  it('allows deleting Dapr service', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).eventually.to.be.equal('ok')
  })

  it('allows deleting Dapr service but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteDaprService()).eventually.to.be.rejectedWith('error!!')
  })

  it('allows deleting Event Store', async () => {
    stub(helmManager, 'exec').resolves({ stdout: 'ok' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).eventually.to.be.equal('ok')
  })

  it('allows deleting Event Store but helms fails', async () => {
    stub(helmManager, 'exec').resolves({ stderr: 'error!!' })
    stub(daprManager, 'readDaprComponentFile').resolves(stateStore.template)
    await expect(daprManager.deleteEventStore()).eventually.to.be.rejectedWith('error!!')
  })
})
