/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { searchReadModel } from '../../src/library/searcher-adapter'
import { createStubInstance, fake, restore, stub, SinonStubbedInstance } from 'sinon'
import { BoosterConfig, FilterFor, Logger } from '@boostercloud/framework-types'
import { random } from 'faker'
import { DynamoDB } from 'aws-sdk'

describe('Searcher adapter', () => {
  describe('The "searchReadModel" method', () => {
    const config: BoosterConfig = new BoosterConfig('test')
    const logger: Logger = {
      info: fake(),
      error: fake(),
      debug: fake(),
    }
    const readModelName: string = random.word()

    let database: SinonStubbedInstance<DynamoDB.DocumentClient>
    const expectedParams = {
      TableName: config.resourceNames.forReadModel(readModelName),
      ConsistentRead: true,
    }

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
      database = createStubInstance(DynamoDB.DocumentClient, {
        scan: {
          promise: stub().returns({
            result: stub().returns({}),
          }),
        } as any,
      })
    })
    after(() => {
      restore()
    })

    it('Executes query without filters', async () => {
      const result = await searchReadModel(database, config, logger, readModelName, {})

      expect(database.scan).to.have.been.calledWithExactly(expectedParams)
      expect(result).to.be.deep.equal([])
    })

    it('Executes query with simple filters', async () => {
      const expectedInput = {
        ...expectedParams,
        FilterExpression: [
          'contains(#id, :id_0)',
          'AND #id IN (:id_1_0,:id_1_1,:id_1_2)',
          'AND #stock > :stock_0',
          'AND #stock <= :stock_1',
        ].join(' '),
        ExpressionAttributeNames: { '#id': 'id', '#in': 'in', '#stock': 'stock' },
        ExpressionAttributeValues: {
          ':id_0': '3',
          ':id_1_0': 'test1',
          ':id_1_1': 'test2',
          ':id_1_2': 'test3',
          ':stock_0': 0,
          ':stock_1': 10,
        },
      }
      const filters: FilterFor<Product> = {
        id: { contains: '3', in: ['test1', 'test2', 'test3'] },
        stock: { gt: 0, lte: 10 },
        // days: { includes: 2 },
        // items: { includes: { sku: '2', price: { cents: 8, currency: 'EUR' } } },
      }

      await searchReadModel(database, config, logger, readModelName, filters as FilterFor<any>)

      expect(database.scan).to.have.been.calledWithExactly(expectedInput)
    })

    it('Executes query with using NOT in filters', async () => {
      const expectedInput = {
        ...expectedParams,
        FilterExpression: 'contains(#id, :id_0) AND NOT (#id = :id_1)',
        ExpressionAttributeNames: { '#id': 'id' },
        ExpressionAttributeValues: {
          ':id_0': '3',
          ':id_1': '333',
        },
      }
      const filters: FilterFor<Product> = {
        id: { contains: '3' },
        not: { id: { eq: '333' } },
      }

      await searchReadModel(database, config, logger, readModelName, filters as FilterFor<any>)

      expect(database.scan).to.have.been.calledWithExactly(expectedInput)
    })

    it('Executes query with using AND & OR filters', async () => {
      const expectedInput = {
        ...expectedParams,
        FilterExpression: [
          '#id <> :id_0',
          'AND (begins_with(#id, :id_1) or begins_with(#id, :id_2))',
          'AND (contains(#id, :id_3) and contains(#id, :id_4))',
        ].join(' '),
        ExpressionAttributeNames: { '#id': 'id' },
        ExpressionAttributeValues: {
          ':id_0': 'test',
          ':id_1': '1',
          ':id_2': '2',
          ':id_3': '3',
          ':id_4': '4',
        },
      }
      const filters: FilterFor<Product> = {
        id: { ne: 'test' },
        or: [{ id: { beginsWith: '1' } }, { id: { beginsWith: '2' } }],
        and: [{ id: { contains: '3' } }, { id: { contains: '4' } }],
      }

      await searchReadModel(database as any, config, logger, readModelName, filters as FilterFor<any>)

      expect(database.scan).to.have.been.calledWithExactly(expectedInput)
    })

    it('Executes query with using nested filters', async () => {
      const expectedInput = {
        ...expectedParams,
        FilterExpression: '#mainItem.#sku = :sku_0 AND #mainItem.#price.#cents >= :cents_0 AND #cents < :cents_1',
        ExpressionAttributeNames: {
          '#mainItem': 'mainItem',
          '#sku': 'sku',
          '#price': 'price',
          '#cents': 'cents',
        },
        ExpressionAttributeValues: { ':sku_0': 'test', ':cents_0': 1000, ':cents_1': 100000 },
      }
      const filters: FilterFor<Product> = {
        mainItem: {
          sku: { eq: 'test' },
          price: {
            cents: { gte: 1000, lt: 100000 },
          },
        },
      }

      await searchReadModel(database, config, logger, readModelName, filters as FilterFor<any>)

      expect(database.scan).to.have.been.calledWithExactly(expectedInput)
    })

    it('Thorw an error with non supported filters', async () => {
      const unknownOperator = 'existsIn'
      const filters: FilterFor<any> = {
        id: { [unknownOperator]: 'test' },
      }

      await expect(searchReadModel(database, config, logger, readModelName, filters)).to.be.eventually.rejectedWith(
        `Operator "${unknownOperator}" is not supported`
      )
    })
  })
})
