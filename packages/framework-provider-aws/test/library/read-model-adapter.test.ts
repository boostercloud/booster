/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { replace, fake } from 'sinon'
import { fetchReadModel, storeReadModel } from '../../src/library/read-model-adapter'
import { DynamoDB } from 'aws-sdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

describe('the "fetchReadModel" method', () => {
  it("responds with an error when the read model doesn't exist", async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'get',
      fake.returns({
        promise: fake.rejects('not found'),
      })
    )

    await expect(fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')).to.be.eventually.rejectedWith(
      'not found'
    )

    expect(db.get).to.have.been.calledOnceWith({
      TableName: 'new-booster-app-app-SomeReadModel',
      Key: { id: 'someReadModelID' },
    })
  })

  it('responds with a read model when it exist', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'get',
      fake.returns({
        promise: fake.resolves({ Item: { some: 'object' } }),
      })
    )

    const result = await fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')

    expect(db.get).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-app-SomeReadModel',
      Key: { id: 'someReadModelID' },
    })
    expect(result).to.deep.equal({ some: 'object' })
  })
})

describe('the "storeReadModel" method', () => {
  it('saves a read model', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'put',
      fake.returns({
        promise: fake.resolves({
          $response: {},
        }),
      })
    )

    const something = await storeReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any)

    expect(db.put).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-app-SomeReadModel',
      Item: { id: 777, some: 'object' },
    })
    expect(something).not.to.be.null
  })
})
