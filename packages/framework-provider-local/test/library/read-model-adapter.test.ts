import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub } from 'sinon'
import { ReadModelRegistry } from '../../src/services'
import { BoosterConfig, Logger, ReadModelEnvelope, ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { expect } from '../expect'

import { random } from 'faker'
import { createMockReadModelEnvelope } from '../helpers/read-model-helper'
import {
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  searchReadModel,
  storeReadModel,
} from '../../src/library/read-model-adapter'

describe('read-models-adapter', () => {
  let mockConfig: BoosterConfig
  let mockLogger: Logger
  let mockReadModel: ReadModelEnvelope

  let loggerDebugStub: SinonStub
  let storeStub: SinonStub
  let queryStub: SinonStub

  let mockReadModelRegistry: SinonStubbedInstance<ReadModelRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockConfig.appName = 'nuke-button'

    loggerDebugStub = stub()
    storeStub = stub()
    queryStub = stub()

    mockLogger = {
      info: fake(),
      warn: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
    mockReadModelRegistry = createStubInstance(ReadModelRegistry)
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
        await fetchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)
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
        await fetchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)
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

      await storeReadModel(
        mockReadModelRegistry,
        mockConfig,
        mockLogger,
        mockReadModel.typeName,
        mockReadModel.value,
        1
      )
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
      await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {})
      expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName })
    })

    describe('query by one field', () => {
      it('eq query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { eq: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': 1 })
      })

      it('ne query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { ne: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $ne: 1 } })
      })

      it('lt query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { lt: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $lt: 1 } })
      })

      it('gt query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { gt: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $gt: 1 } })
      })

      it('lte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { lte: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $lte: 1 } })
      })

      it('gte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { gte: 1 },
        })
        expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $gte: 1 } })
      })

      it('gte query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { in: [1, 2, 3] },
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          'value.foo': { $in: [1, 2, 3] },
        })
      })

      it('contains query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { contains: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          'value.foo': { $regex: new RegExp('bar') },
        })
      })

      it('includes query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { includes: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          'value.foo': { $regex: new RegExp('bar') },
        })
      })

      it('beginsWith query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          foo: { beginsWith: 'bar' },
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          'value.foo': { $regex: new RegExp('^bar') },
        })
      })

      it('NOT beginsWith query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          not: { foo: { beginsWith: 'bar' } },
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          $not: { 'value.foo': { $regex: new RegExp('^bar') }, typeName: mockReadModel.typeName },
        })
      })
    })

    describe('multiple queries', () => {
      it('gt lt AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          and: [{ foo: { gt: 1 } }, { foo: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          $and: [
            { 'value.foo': { $gt: 1 }, typeName: mockReadModel.typeName },
            { 'value.foo': { $lt: 10 }, typeName: mockReadModel.typeName },
          ],
        })
      })

      it('gte lte AND query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          and: [{ foo: { gte: 1 } }, { foo: { lte: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          $and: [
            { 'value.foo': { $gte: 1 }, typeName: mockReadModel.typeName },
            { 'value.foo': { $lte: 10 }, typeName: mockReadModel.typeName },
          ],
        })
      })

      it('OR query should call read model registry store with the appropriate operation converted', async () => {
        const mockReadModel = createMockReadModelEnvelope()
        await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
          or: [{ foo: { eq: 1 } }, { bar: { lt: 10 } }],
        })
        expect(queryStub).to.have.been.calledWithExactly({
          typeName: mockReadModel.typeName,
          $or: [
            { 'value.foo': 1, typeName: mockReadModel.typeName },
            { 'value.bar': { $lt: 10 }, typeName: mockReadModel.typeName },
          ],
        })
      })
    })
  })
})
