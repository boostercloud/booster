/* eslint-disable @typescript-eslint/ban-ts-comment */
import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub } from 'sinon'
import { GraphQLService, ReadModelRegistry } from '../../src'
import {
  BoosterConfig,
  FilterFor,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  SortFor,
  UserApp,
  UUID,
} from '@boostercloud/framework-types'
import { expect } from '../expect'
import { random } from 'faker'
import { createMockReadModelEnvelope } from '../helpers/read-model-helper'
import {
  deleteReadModel,
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  searchReadModel,
  storeReadModel,
} from '../../src/library/read-model-adapter'
import { describe } from 'mocha'

async function fetchMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockReadModelTypeName: string,
  mockReadModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  // @ts-ignore
  return await fetchReadModel(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID)
}

async function storeMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockReadModel: ReadModelEnvelope
): Promise<void> {
  const mockUserApp: UserApp = {} as any
  const graphQLService = new GraphQLService(mockUserApp)
  stub(graphQLService, 'handleNotificationSubscription')
  await storeReadModel(
    graphQLService,
    // @ts-ignore
    mockReadModelRegistry,
    mockConfig,
    mockReadModel.typeName,
    mockReadModel.value,
    1
  )
}

async function searchMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockReadModel: ReadModelEnvelope,
  filters: FilterFor<any>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined
): Promise<void> {
  // @ts-ignore
  await searchReadModel(
    mockReadModelRegistry as any,
    mockConfig,
    mockReadModel.typeName,
    filters,
    sortBy,
    limit,
    afterCursor
  )
}

describe('read-models-adapter', () => {
  let mockConfig: BoosterConfig
  let mockReadModel: ReadModelEnvelope

  let loggerDebugStub: SinonStub
  let storeStub: SinonStub
  let queryStub: SinonStub
  let deleteStub: SinonStub

  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockConfig.appName = 'nuke-button'
    mockConfig.enableSubscriptions = true

    loggerDebugStub = stub()
    storeStub = stub()
    queryStub = stub()
    deleteStub = stub()

    mockConfig.logger = {
      info: fake(),
      warn: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
    mockReadModelRegistry = createStubInstance(ReadModelRegistry) as StubbedClass<ReadModelRegistry>
    mockReadModel = createMockReadModelEnvelope()

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockReadModelRegistry, 'store', storeStub as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockReadModelRegistry, 'query', queryStub as any)
    replace(mockReadModelRegistry, 'deleteById', deleteStub as any)
  })

  describe('rawReadModelEventsToEnvelopes', () => {
    it('should return an empty array of envelopes', async () => {
      const results = await rawReadModelEventsToEnvelopes(mockConfig, [])
      const expected: ReadModelEnvelope[] = []
      expect(results).to.deep.equal(expected)
    })

    it('should return an array of envelopes', async () => {
      const value1: ReadModelEnvelope = createMockReadModelEnvelope()
      const value2: ReadModelEnvelope = createMockReadModelEnvelope()
      const rawEvents: unknown[] = [value1 as unknown, value2 as unknown]
      const results = await rawReadModelEventsToEnvelopes(mockConfig, rawEvents)
      const expected: ReadModelEnvelope[] = [value1, value2]
      expect(results).to.deep.equal(expected)
    })
  })

  describe('fetchReadModel', () => {
    let mockReadModelTypeName: string
    let mockReadModelID: UUID

    beforeEach(() => {
      mockReadModelTypeName = random.alphaNumeric(10)
      mockReadModelID = random.uuid()
    })

    it('should call read model registry query and return a value', async () => {
      queryStub.resolves([mockReadModel])
      const result: ReadModelInterface = (
        await fetchMock(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID)
      )[0]

      expect(queryStub).to.have.been.calledOnceWithExactly({
        'value.id': mockReadModelID,
        typeName: mockReadModelTypeName,
      })
      expect(result).to.deep.equal(mockReadModel.value)
      expect(mockConfig.logger?.debug).to.not.be.calledWith(
        '[Booster]|read-model-adapter#fetchReadModel: ',
        `Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`
      )
      expect(mockConfig.logger?.debug).to.be.calledWith(
        '[Booster]|read-model-adapter#fetchReadModel: ',
        `Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`
      )
    })

    it('should call read model registry query and no results', async () => {
      queryStub.resolves([])
      const result = (await fetchMock(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID))[0]

      expect(queryStub).to.have.been.calledOnceWithExactly({
        'value.id': mockReadModelID,
        typeName: mockReadModelTypeName,
      })
      expect(result).to.be.undefined
      expect(mockConfig.logger?.debug).to.be.calledWith(
        '[Booster]|read-model-adapter#fetchReadModel: ',
        `Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`
      )
      expect(mockConfig.logger?.debug).to.not.be.calledWith(
        `[ReadModelAdapter#fetchReadModel] Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`
      )
    })
  })

  describe('storeReadModel', () => {
    let mockReadModel: ReadModelEnvelope

    beforeEach(async () => {
      mockReadModel = createMockReadModelEnvelope()

      await storeMock(mockReadModelRegistry, mockConfig, mockReadModel)
    })

    it('should call read model registry store', () => {
      expect(storeStub).to.have.been.calledWithExactly(mockReadModel, 1)
    })

    it('should log the right debug message', () => {
      expect(mockConfig.logger?.debug).to.have.been.calledWithExactly(
        '[Booster]|read-model-adapter#storeReadModel: ',
        'Read model stored'
      )
    })
  })

  describe('searchReadModel', () => {
    it('empty query should call read model registry store', async () => {
      const mockReadModel = createMockReadModelEnvelope()
      await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {})
      expect(queryStub).to.have.been.calledWithExactly(
        {
          typeName: mockReadModel.typeName,
        },
        undefined,
        0,
        undefined
      )
    })

    describe('query by one field', () => {
      it('eq query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { eq: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': 1 },
          undefined,
          0,
          undefined
        )
      })

      it('ne query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { ne: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': { $ne: 1 } },
          undefined,
          0,
          undefined
        )
      })

      it('lt query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { lt: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': { $lt: 1 } },
          undefined,
          0,
          undefined
        )
      })

      it('gt query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { gt: 1 },
        })

        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': { $gt: 1 } },
          undefined,
          0,
          undefined
        )
      })

      it('lte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { lte: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': { $lte: 1 } },
          undefined,
          0,
          undefined
        )
      })

      it('gte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { gte: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName, 'value.foo': { $gte: 1 } },
          undefined,
          0,
          undefined
        )
      })

      it('gte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { in: [1, 2, 3] },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            'value.foo': { $in: [1, 2, 3] },
          },
          undefined,
          0,
          undefined
        )
      })

      it('contains query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { contains: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            'value.foo': { $regex: new RegExp('bar') },
          },
          undefined,
          0,
          undefined
        )
      })

      it('includes query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { includes: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            'value.foo': { $regex: new RegExp('bar') },
          },
          undefined,
          0,
          undefined
        )
      })

      it('includes object query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { includes: { bar: 'baz' } },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            'value.foo': { $elemMatch: { bar: 'baz' } },
          },
          undefined,
          0,
          undefined
        )
      })

      it('beginsWith query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: { beginsWith: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            'value.foo': { $regex: new RegExp('^bar') },
          },
          undefined,
          0,
          undefined
        )
      })

      it('NOT beginsWith query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          not: { foo: { beginsWith: 'bar' } },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $not: { 'value.foo': { $regex: new RegExp('^bar') } },
          },
          undefined,
          0,
          undefined
        )
      })
    })

    describe('multiple queries', () => {
      it('only fields query should use AND and call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          foo: {
            subFooField: { eq: 'subFooField' },
          },
          bar: {
            subBarField: { eq: 'subBarField' },
          },
          other: {
            subOtherField: { ne: true },
          },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            'value.foo.subFooField': 'subFooField',
            'value.bar.subBarField': 'subBarField',
            'value.other.subOtherField': { $ne: true },
            typeName: mockReadModel.typeName,
          },
          undefined,
          0,
          undefined
        )
      })

      it('gt lt AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          and: [{ foo: { gt: 1 } }, { foo: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $and: [{ 'value.foo': { $gt: 1 } }, { 'value.foo': { $lt: 10 } }],
          },
          undefined,
          0,
          undefined
        )
      })

      it('gte lte AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          and: [{ foo: { gte: 1 } }, { foo: { lte: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $and: [{ 'value.foo': { $gte: 1 } }, { 'value.foo': { $lte: 10 } }],
          },
          undefined,
          0,
          undefined
        )
      })

      it('OR query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
          or: [{ foo: { eq: 1 } }, { bar: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $or: [{ 'value.foo': 1 }, { 'value.bar': { $lt: 10 } }],
          },
          undefined,
          0,
          undefined
        )
      })
    })

    describe('Sort fields', () => {
      it('query should call read model registry store with sort fields, limits and skip', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(
          mockReadModelRegistry,
          mockConfig,
          mockReadModel,
          {},
          [
            {
              field: 'ID',
              order: 'DESC',
            },
            {
              field: 'anotherField',
              order: 'ASC',
            },
          ],
          3,
          { id: '5' }
        )
        expect(queryStub).to.have.been.calledWithExactly(
          { typeName: mockReadModel.typeName },
          [
            { field: 'ID', order: 'DESC' },
            {
              field: 'anotherField',
              order: 'ASC',
            },
          ],
          5,
          3
        )
      })
    })
  })

  describe('deleteReadModel', () => {
    it('delete one read model', async () => {
      const expectedId = random.uuid()
      const mockReadModelInterface: ReadModelInterface = {
        id: expectedId,
        age: random.number(40),
        foo: random.word(),
        bar: random.float(),
        boosterMetadata: {
          version: 1,
          schemaVersion: 1,
        },
      }
      const expectedName = 'readModel'
      await deleteReadModel(mockReadModelRegistry as any, mockConfig, expectedName, mockReadModelInterface)
      expect(deleteStub).to.have.been.calledWithExactly(expectedId, expectedName)
    })
  })
})
