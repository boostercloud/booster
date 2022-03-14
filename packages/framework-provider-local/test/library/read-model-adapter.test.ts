/* eslint-disable @typescript-eslint/ban-ts-comment */
import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub } from 'sinon'
import { ReadModelRegistry } from '../../src'
import {
  BoosterConfig,
  FilterFor,
  Logger,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  SortFor,
  UUID,
} from '@boostercloud/framework-types'
import { expect } from '../expect'

import { random } from 'faker'
import { createMockReadModelEnvelope } from '../helpers/read-model-helper'
import {
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  searchReadModel,
  storeReadModel,
} from '../../src/library/read-model-adapter'

async function fetchMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockLogger: Logger,
  mockReadModelTypeName: string,
  mockReadModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  // @ts-ignore
  return await fetchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)
}

async function storeMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockLogger: Logger,
  mockReadModel: ReadModelEnvelope
) {
  // @ts-ignore
  await storeReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, mockReadModel.value, 1)
}

async function searchMock(
  mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>,
  mockConfig: BoosterConfig,
  mockLogger: Logger,
  mockReadModel: ReadModelEnvelope,
  filters: FilterFor<any>,
  sortBy?: Array<SortFor>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined
) {
  // @ts-ignore
  await searchReadModel(
    mockReadModelRegistry as any,
    mockConfig,
    mockLogger,
    mockReadModel.typeName,
    filters,
    sortBy,
    limit,
    afterCursor
  )
}

describe('read-models-adapter', () => {
  let mockConfig: BoosterConfig
  let mockLogger: Logger
  let mockReadModel: ReadModelEnvelope

  let loggerDebugStub: SinonStub
  let storeStub: SinonStub
  let queryStub: SinonStub

  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockConfig.appName = 'nuke-button'

    loggerDebugStub = stub()
    storeStub = stub()
    queryStub = stub()

    mockLogger = {
      info: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
    mockReadModelRegistry = createStubInstance(ReadModelRegistry) as StubbedClass<ReadModelRegistry>
    mockReadModel = createMockReadModelEnvelope()

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockReadModelRegistry, 'store', storeStub as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockReadModelRegistry, 'query', queryStub as any)
  })

  describe('rawReadModelEventsToEnvelopes', () => {
    it('should return an empty array of envelopes', async () => {
      const results = await rawReadModelEventsToEnvelopes(mockConfig, mockLogger, [])
      const expected: ReadModelEnvelope[] = []
      expect(results).to.deep.equal(expected)
    })

    it('should return an array of envelopes', async () => {
      const value1: ReadModelEnvelope = createMockReadModelEnvelope()
      const value2: ReadModelEnvelope = createMockReadModelEnvelope()
      const rawEvents: unknown[] = [value1 as unknown, value2 as unknown]
      const results = await rawReadModelEventsToEnvelopes(mockConfig, mockLogger, rawEvents)
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
        await fetchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)
      )[0]

      expect(queryStub).to.have.been.calledOnceWithExactly({
        'value.id': mockReadModelID,
        typeName: mockReadModelTypeName,
      })
      expect(result).to.deep.equal(mockReadModel.value)
      expect(mockLogger.debug).to.not.be.calledWith(
        `[ReadModelAdapter#fetchReadModel] Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`
      )
      expect(mockLogger.debug).to.be.calledWith(
        `[ReadModelAdapter#fetchReadModel] Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`
      )
    })

    it('should call read model registry query and no results', async () => {
      queryStub.resolves([])
      const result = (
        await fetchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)
      )[0]

      expect(queryStub).to.have.been.calledOnceWithExactly({
        'value.id': mockReadModelID,
        typeName: mockReadModelTypeName,
      })
      expect(result).to.be.undefined
      expect(mockLogger.debug).to.be.calledWith(
        `[ReadModelAdapter#fetchReadModel] Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`
      )
      expect(mockLogger.debug).to.not.be.calledWith(
        `[ReadModelAdapter#fetchReadModel] Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`
      )
    })
  })

  describe('storeReadModel', () => {
    let mockReadModel: ReadModelEnvelope

    beforeEach(async () => {
      mockReadModel = createMockReadModelEnvelope()

      await storeMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel)
    })

    it('should call read model registry store', () => {
      expect(storeStub).to.have.been.calledWithExactly(mockReadModel)
    })

    it('should log the right debug message', () => {
      expect(mockLogger.debug).to.have.been.calledWithExactly('[ReadModelAdapter#storeReadModel] Read model stored')
    })
  })

  describe('searchReadModel', () => {
    it('empty query should call read model registry store', async () => {
      const mockReadModel = createMockReadModelEnvelope()
      await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {})
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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

      it('beginsWith query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
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
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
          not: { foo: { beginsWith: 'bar' } },
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $not: { 'value.foo': { $regex: new RegExp('^bar') }, typeName: mockReadModel.typeName },
          },
          undefined,
          0,
          undefined
        )
      })
    })

    describe('multiple queries', () => {
      it('gt lt AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
          and: [{ foo: { gt: 1 } }, { foo: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $and: [
              { 'value.foo': { $gt: 1 }, typeName: mockReadModel.typeName },
              { 'value.foo': { $lt: 10 }, typeName: mockReadModel.typeName },
            ],
          },
          undefined,
          0,
          undefined
        )
      })

      it('gte lte AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
          and: [{ foo: { gte: 1 } }, { foo: { lte: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $and: [
              { 'value.foo': { $gte: 1 }, typeName: mockReadModel.typeName },
              { 'value.foo': { $lte: 10 }, typeName: mockReadModel.typeName },
            ],
          },
          undefined,
          0,
          undefined
        )
      })

      it('OR query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchMock(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel, {
          or: [{ foo: { eq: 1 } }, { bar: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly(
          {
            typeName: mockReadModel.typeName,
            $or: [
              { 'value.foo': 1, typeName: mockReadModel.typeName },
              { 'value.bar': { $lt: 10 }, typeName: mockReadModel.typeName },
            ],
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
          mockLogger,
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
})
