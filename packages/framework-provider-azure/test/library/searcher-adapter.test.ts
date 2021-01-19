/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { searchReadModel } from '../../src/library/searcher-adapter'
import { createStubInstance, fake, match, restore, stub, SinonStubbedInstance } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, FilterOld, Logger } from '@boostercloud/framework-types'
import { random } from 'faker'

describe('Searcher adapter', () => {
  describe('The "searchReadModel" method', () => {
    let mockLogger: Logger
    let mockConfig: BoosterConfig

    let mockReadModelName: string

    let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>

    beforeEach(() => {
      mockConfig = new BoosterConfig('test')
      mockLogger = {
        info: fake(),
        error: fake(),
        debug: fake(),
      }
      mockCosmosDbClient = createStubInstance(CosmosClient, {
        database: stub().returns({
          container: stub().returns({
            items: {
              query: stub().returns({
                fetchAll: fake.resolves({ resources: [] }) as any,
              }),
            },
          }),
        }) as any,
      })
      mockReadModelName = random.word()
    })

    afterEach(() => {
      restore()
    })

    it('Executes a SQL query without filters in the read model table', async () => {
      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, {})

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c ',
          parameters: [],
        })
      )
    })

    it('Executes a SQL query with filters in the read model table', async () => {
      const filters: Record<string, FilterOld<any>> = {
        propertyA: { operation: '=', values: [1] },
        propertyB: { operation: '!=', values: ['a'] },
        propertyC: { operation: 'between', values: [1, 100] },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["propertyA"] = @propertyA_0 ' +
            'AND c["propertyB"] <> @propertyB_0 ' +
            'AND c["propertyC"] BETWEEN @propertyC_0 AND @propertyC_1',
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

    it('Supports comparison operators', async () => {
      const filters: Record<string, FilterOld<any>> = {
        propertyA: { operation: '<', values: [100] },
        propertyB: { operation: '>', values: [0] },
        propertyC: { operation: '>=', values: [0] },
        propertyD: { operation: '<=', values: [100] },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["propertyA"] < @propertyA_0 ' +
            'AND c["propertyB"] > @propertyB_0 ' +
            'AND c["propertyC"] >= @propertyC_0 ' +
            'AND c["propertyD"] <= @propertyD_0',
          parameters: [
            {
              name: '@propertyA_0',
              value: 100,
            },
            {
              name: '@propertyB_0',
              value: 0,
            },
            {
              name: '@propertyC_0',
              value: 0,
            },
            {
              name: '@propertyD_0',
              value: 100,
            },
          ],
        })
      )
    })

    it('Supports other operators', async () => {
      const filters: Record<string, FilterOld<any>> = {
        propertyA: { operation: 'in', values: [1, 2] },
        propertyB: { operation: 'contains', values: ['a'] },
        propertyC: { operation: 'not-contains', values: ['x'] },
        propertyD: { operation: 'begins-with', values: ['a'] },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["propertyA"] IN (@propertyA_0,@propertyA_1) ' +
            'AND CONTAINS(c["propertyB"], @propertyB_0) ' +
            'AND NOT CONTAINS(c["propertyC"], @propertyC_0) ' +
            'AND STARTSWITH(c["propertyD"], @propertyD_0)',
          parameters: [
            {
              name: '@propertyA_0',
              value: 1,
            },
            {
              name: '@propertyA_1',
              value: 2,
            },
            {
              name: '@propertyB_0',
              value: 'a',
            },
            {
              name: '@propertyC_0',
              value: 'x',
            },
            {
              name: '@propertyD_0',
              value: 'a',
            },
          ],
        })
      )
    })
  })
})
