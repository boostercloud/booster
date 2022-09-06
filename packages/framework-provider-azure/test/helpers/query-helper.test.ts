/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { search } from '../../src/helpers/query-helper'
import { createStubInstance, fake, match, restore, stub, SinonStubbedInstance } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, FilterFor } from '@boostercloud/framework-types'
import { random } from 'faker'

describe('Query helper', () => {
  describe('The "search" method', () => {
    let mockConfig: BoosterConfig

    let mockReadModelName: string

    let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>
    class Money {
      constructor(public cents: number | null, public currency: string) {}
    }

    class Item {
      constructor(public sku: string | null, public price: Money) {}
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
      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, {})

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockReadModelName}`)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c ',
          parameters: [],
        })
      )
    })

    it('Executes a SQL query with a projection in the read model table', async () => {
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        {},
        undefined,
        undefined,
        false,
        undefined,
        'DISTINCT field'
      )

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockReadModelName}`)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT DISTINCT field FROM c ',
          parameters: [],
        })
      )
    })

    it('Executes a SQL query with filters in the read model table', async () => {
      const filters: FilterFor<Product> = {
        id: { eq: '3', in: ['test1', 'test2', 'test3'] },
        stock: { gt: 0, lte: 10 },
      }

      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockReadModelName}`)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
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

      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
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

      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
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

      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE c["mainItem"]["sku"] = @sku_0 ' +
            'AND c["mainItem"]["price"]["cents"] >= @cents_0 AND c["mainItem"]["price"]["cents"] < @cents_1',
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

      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true)',
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

    it('Supports order for 1 field', async () => {
      const filters: FilterFor<Product> = {}
      const order = { sku: 'DESC' }
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        filters,
        undefined,
        undefined,
        undefined,
        order
      )

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c  ORDER BY c["sku"] DESC',
          parameters: [],
        })
      )
    })

    it('Supports order for any number of fields', async () => {
      const filters: FilterFor<Product> = {}
      const order = { sku: 'DESC', price: 'ASC' }
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        filters,
        undefined,
        undefined,
        undefined,
        order
      )

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c  ORDER BY c["sku"] DESC, c["price"] ASC',
          parameters: [],
        })
      )
    })

    it('Supports order for nested fields', async () => {
      const filters: FilterFor<Product> = {}
      const order = { sku: 'DESC', address: { street: 'ASC' } }
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        filters,
        undefined,
        undefined,
        undefined,
        order
      )

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query: 'SELECT * FROM c  ORDER BY c["sku"] DESC, c["address"]["street"] ASC',
          parameters: [],
        })
      )
    })

    it('Supports limited results', async () => {
      const filters: FilterFor<Product> = {
        days: { includes: 2 },
        items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
      }
      const order = { sku: 'DESC', price: 'ASC' }
      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters, 3, undefined, false, order)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true) ORDER BY c["sku"] DESC, c["price"] ASC OFFSET 0 LIMIT 3 ',
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

    it('Supports paginated and limited results', async () => {
      const filters: FilterFor<Product> = {
        days: { includes: 2 },
        items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
      }
      const order = { sku: 'DESC', price: 'ASC' }
      await search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters, 3, { id: '3' }, true, order)

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true) ORDER BY c["sku"] DESC, c["price"] ASC OFFSET 3 LIMIT 3 ',
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

    it('supports isDefine filters', async () => {
      const filters: FilterFor<Product> = {
        and: [
          { days: { isDefined: true } },
          { mainItem: { isDefined: false } },
          { mainItem: { sku: { isDefined: true } } },
        ],
      }
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        filters,
        undefined,
        undefined,
        undefined,
        undefined
      )

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE (IS_DEFINED(c["days"]) and NOT IS_DEFINED(c["mainItem"]) and IS_DEFINED(c["mainItem"]["sku"]))',
          parameters: [],
        })
      )
    })

    it('supports isDefine filters with complex filters', async () => {
      const filters: FilterFor<Product> = {
        and: [
          {
            id: { eq: '3' },
          },
          {
            mainItem: {
              sku: {
                eq: 'test',
              },
            },
          },
          {
            or: [
              {
                days: { isDefined: true },
              },
              {
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
              },
            ],
          },
          { mainItem: { sku: { eq: null } } },
          { mainItem: { price: { cents: { ne: null } } } },
        ],
      }
      await search(
        mockCosmosDbClient as any,
        mockConfig,
        mockReadModelName,
        filters,
        undefined,
        undefined,
        undefined,
        undefined
      )

      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
          .query
      ).to.have.been.calledWith(
        match({
          query:
            'SELECT * FROM c WHERE (c["id"] = @id_0 and c["mainItem"]["sku"] = @sku_0 and (IS_DEFINED(c["days"]) or ARRAY_CONTAINS(c["items"], @items_0, true)) and c["mainItem"]["sku"] = @sku_1 and c["mainItem"]["price"]["cents"] <> @cents_0)',
          parameters: [
            { name: '@id_0', value: '3' },
            { name: '@sku_0', value: 'test' },
            { name: '@items_0', value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
            { name: '@sku_1', value: null },
            { name: '@cents_0', value: null },
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
      expect(search(mockCosmosDbClient as any, mockConfig, mockReadModelName, filters)).to.be.eventually.rejectedWith(
        `Operator "${unknownOperator}" is not supported`
      )
    })
  })
})
