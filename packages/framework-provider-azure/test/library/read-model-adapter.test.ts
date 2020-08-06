/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, fake, match } from 'sinon'
import { fetchReadModel, storeReadModel } from '../../src/library/read-model-adapter'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import sinon = require('sinon')

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

const cosmosDb = createStubInstance(CosmosClient, {
  database: sinon.stub().returns({
    container: sinon.stub().returns({
      items: {
        query: sinon.stub().returns({
          fetchAll: fake.resolves({ resources: [] }) as any,
        }),
        upsert: sinon.stub().returns(fake.resolves({})),
      },
      item: sinon.stub().returns({
        read: sinon.stub().returns(fake.resolves({})),
      }),
    }),
  }) as any,
})
const config = new BoosterConfig('test')

describe('the "fetchReadModel" method', () => {
  it('responds with a read model when it exist', async () => {
    const result = await fetchReadModel(
      (cosmosDb as unknown) as CosmosClient,
      config,
      logger,
      'SomeReadModel',
      'someReadModelID'
    )

    expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
    expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
      `${config.resourceNames.applicationStack}-SomeReadModel`
    )
    expect(
      cosmosDb
        .database(config.resourceNames.applicationStack)
        .container(`${config.resourceNames.applicationStack}-SomeReadModel`).item
    ).to.have.been.calledWithExactly('someReadModelID', 'someReadModelID')
    expect(result).not.to.be.null
  })
})

describe('the "storeReadModel" method', () => {
  it('saves a read model', async () => {
    const something = await storeReadModel(cosmosDb as any, config, logger, 'SomeReadModel', {
      id: 777,
      some: 'object',
    } as any)

    expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
    expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
      `${config.resourceNames.applicationStack}-SomeReadModel`
    )
    expect(
      cosmosDb
        .database(config.resourceNames.applicationStack)
        .container(`${config.resourceNames.applicationStack}-SomeReadModel`).items.upsert
    ).to.have.been.calledWithExactly(
      match({
        id: 777,
        some: 'object',
      })
    )
    expect(something).not.to.be.null
  })
})
