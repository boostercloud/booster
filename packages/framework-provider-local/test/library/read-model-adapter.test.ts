import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub } from 'sinon'
import { ReadModelRegistry } from '../../src/services'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { ReadModelEnvelope, UUID } from '@boostercloud/framework-types'
import { random } from 'faker'
import { createMockReadModelEnvelope } from '../helpers/read-model-helper'
import { fetchReadModel, searchReadModel, storeReadModel } from '../../src/library/read-model-adapter'

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

  describe('fetchReadModel', () => {
    let mockReadModelTypeName: string
    let mockReadModelID: UUID

    beforeEach(() => {
      queryStub.resolves([mockReadModel])

      mockReadModelTypeName = random.alphaNumeric(10)
      mockReadModelID = random.uuid()
    })

    it('should call read model registry query', async () => {
      await fetchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModelTypeName, mockReadModelID)

      expect(queryStub).to.have.been.calledOnceWithExactly({
        value: { id: mockReadModelID },
        typeName: mockReadModelTypeName,
      })
    })
  })

  describe('storeReadModel', () => {
    let mockReadModel: ReadModelEnvelope

    beforeEach(async () => {
      mockReadModel = createMockReadModelEnvelope()

      await storeReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, mockReadModel.value)
    })

    it('should call read model registry store', () => {
      expect(storeStub).to.have.been.calledWithExactly(mockReadModel)
    })
  })

  describe('searchReadModel', () => {
    it('should call read model registry store', async () => {
      const mockReadModel = createMockReadModelEnvelope()
      await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {})
      expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName })
    })

    it('should call read model registry store with the appropriate operation converted', async () => {
      const mockReadModel = createMockReadModelEnvelope()
      await searchReadModel(mockReadModelRegistry, mockConfig, mockLogger, mockReadModel.typeName, {
        foo: { operation: '>', values: [1] },
      })
      expect(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $gt: 1 } })
    })
  })
})
