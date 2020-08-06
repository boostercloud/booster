/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { searchReadModel } from '../../src/library/searcher-adapter'
import { createStubInstance, fake, match, restore } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, Filter, Logger } from '@boostercloud/framework-types'
import sinon = require('sinon')

const fakeLogger: Logger = {
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
      },
    }),
  }) as any,
})

const config = new BoosterConfig('test')

describe('The "searchReadModel" method', () => {
  afterEach(() => {
    restore()
  })

  it('Executes a SQL query without filters in the read model table', async () => {
    await searchReadModel(cosmosDb as any, config, fakeLogger, 'MyReadModel', {})

    expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
    expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
      `${config.resourceNames.applicationStack}-MyReadModel`
    )
    expect(
      cosmosDb
        .database(config.resourceNames.applicationStack)
        .container(`${config.resourceNames.applicationStack}-MyReadModel`).items.query
    ).to.have.been.calledWith(
      match({
        query: 'SELECT * FROM c ',
        parameters: [],
      })
    )
  })

  it('Executes a SQL query with filters in the read model table', async () => {
    const filters: Record<string, Filter<any>> = {
      propertyA: { operation: '=', values: [1] },
      propertyB: { operation: '!=', values: ['a'] },
      propertyC: { operation: 'between', values: [1, 100] },
    }

    await searchReadModel(cosmosDb as any, config, fakeLogger, 'MyReadModel', filters)

    expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
    expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
      `${config.resourceNames.applicationStack}-MyReadModel`
    )
    expect(
      cosmosDb
        .database(config.resourceNames.applicationStack)
        .container(`${config.resourceNames.applicationStack}-MyReadModel`).items.query
    ).to.have.been.calledWith(
      match({
        query:
          'SELECT * FROM c WHERE c["propertyA"] = @propertyA_0 AND c["propertyB"] <> @propertyB_0 AND c["propertyC"] BETWEEN @propertyC_0 AND @propertyC_1',
        parameters: [
          {
            name: '@propertyA_0',
            value: 1,
          },
          {
            name: '@propertyB_0',
            value: 'a',
          },
          {
            name: '@propertyC_0',
            value: 1,
          },
          {
            name: '@propertyC_1',
            value: 100,
          },
        ],
      })
    )
  })
})
