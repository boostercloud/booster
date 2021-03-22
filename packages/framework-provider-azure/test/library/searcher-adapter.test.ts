/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { searchReadModel } from '../../src/library/searcher-adapter'
import { createStubInstance, fake, match, restore, stub, SinonStubbedInstance } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, FilterFor, Logger } from '@boostercloud/framework-types'
import { random } from 'faker'

describe('Searcher adapter', () => {
  describe('The "searchReadModel" method', () => {
    let mockLogger: Logger
    let mockConfig: BoosterConfig

    let mockReadModelName: string

    let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>
    class Money {
      constructor(public cents: number, public currency: string) {}
    }

    class Item {
      constructor(public sku: string, public price: Money) {}
    }

    class Product {
      constructor(
        readonly id: string,
        readonly stock: number,
        public mainItem: Item,
        public items: Array<Item>,
        public buyers: Array<string>,
        public days: Array<number>,
        public pairs: Array<Array<number>>
      ) {}
    }

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
      const filters: FilterFor<Product> = {
        id: { eq: '3', in: ['test1', 'test2', 'test3'] },
        stock: { gt: 0, lte: 10 },
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
            'SELECT * FROM c WHERE ' +
            'c["id"] = @id_0 AND c["id"] IN (@id_1_0,@id_1_1,@id_1_2) AND ' +
            'c["stock"] > @stock_0 AND c["stock"] <= @stock_1',
          parameters: [
            {
              name: '@id_0',
              value: '3',
            },
            {
              name: '@id_1_0',
              value: 'test1',
            },
            {
              name: '@id_1_1',
              value: 'test2',
            },
            {
              name: '@id_1_2',
              value: 'test3',
            },
            {
              name: '@stock_0',
              value: 0,
            },
            {
              name: '@stock_1',
              value: 10,
            },
          ],
        })
      )
    })

    it('Supports NOT filter combinator', async () => {
      const filters: FilterFor<Product> = {
        id: { contains: '3' },
        not: { id: { eq: '333' } },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c WHERE CONTAINS(c["id"], @id_0) AND NOT (c["id"] = @id_1)',
          parameters: [
            {
              name: '@id_0',
              value: '3',
            },
            {
              name: '@id_1',
              value: '333',
            },
          ],
        })
      )
    })

    it('Supports AND & OR filter combinators', async () => {
      const filters: FilterFor<Product> = {
        id: { ne: 'test' },
        or: [{ id: { beginsWith: '1' } }, { id: { beginsWith: '2' } }],
        and: [{ id: { contains: '3' } }, { id: { contains: '4' } }],
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["id"] <> @id_0 ' +
            'AND (STARTSWITH(c["id"], @id_1) or STARTSWITH(c["id"], @id_2)) ' +
            'AND (CONTAINS(c["id"], @id_3) and CONTAINS(c["id"], @id_4))',
          parameters: [
            {
              name: '@id_0',
              value: 'test',
            },
            {
              name: '@id_1',
              value: '1',
            },
            {
              name: '@id_2',
              value: '2',
            },
            {
              name: '@id_3',
              value: '3',
            },
            {
              name: '@id_4',
              value: '4',
            },
          ],
        })
      )
    })

    it('Supports nested properties filters', async () => {
      const filters: FilterFor<Product> = {
        mainItem: {
          sku: { eq: 'test' },
          price: {
            cents: { gte: 1000, lt: 100000 },
          },
        },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["mainItem"].c["sku"] = @sku_0 ' +
            'AND c["mainItem"].c["price"].c["cents"] >= @cents_0 AND c["cents"] < @cents_1',
          parameters: [
            {
              name: '@sku_0',
              value: 'test',
            },
            {
              name: '@cents_0',
              value: 1000,
            },
            {
              name: '@cents_1',
              value: 100000,
            },
          ],
        })
      )
    })

    it('Supports array includes filter', async () => {
      const filters: FilterFor<Product> = {
        days: { includes: 2 },
        items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
      }

      await searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)

      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c WHERE CONTAINS(c["days"], @days_0) AND CONTAINS(c["items"], @items_0)',
          parameters: [
            {
              name: '@days_0',
              value: 2,
            },
            {
              name: '@items_0',
              value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
            },
          ],
        })
      )
    })

    it('Throws an error with non supported filters', async () => {
      const unknownOperator = 'existsIn'
      const filters: FilterFor<any> = {
        id: { [unknownOperator]: 'test' },
      }

      // eslint-disable-next-line @typescript-eslint/no-floating-promises
      expect(
        searchReadModel(mockCosmosDbClient as any, mockConfig, mockLogger, mockReadModelName, filters)
      ).to.be.eventually.rejectedWith(`Operator "${unknownOperator}" is not supported`)
    })
  })
})
