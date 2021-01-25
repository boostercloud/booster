import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub } from 'sinon'
import { EventRegistry } from '../../src/services'
import { readEntityEventsSince, readEntityLatestSnapshot, storeEvents } from '../../src/library/events-adapter'
import { UserApp, EventEnvelope, UUID, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { createMockEventEnvelop, createMockSnapshot } from '../helpers/event-helper'
import { random, date } from 'faker'

describe('events-adapter', () => {
  let mockConfig: BoosterConfig
  let mockLogger: Logger
  let mockEventEnvelop: EventEnvelope
  let mockSnapshot: EventEnvelope

  let loggerDebugStub: SinonStub
  let storeStub: SinonStub
  let queryStub: SinonStub
  let queryLatestStub: SinonStub
  let boosterEventDispatcherStub: SinonStub

  let mockUserApp: UserApp
  let mockEventRegistry: SinonStubbedInstance<EventRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockConfig.appName = 'nuke-button'

    mockEventEnvelop = createMockEventEnvelop()
    mockSnapshot = createMockSnapshot()

    loggerDebugStub = stub()
    storeStub = stub()
    boosterEventDispatcherStub = stub()
    queryStub = stub()
    queryLatestStub = stub()

    mockLogger = {
      info: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
    mockUserApp = {
      boosterEventDispatcher: boosterEventDispatcherStub,
    } as any
    mockEventRegistry = createStubInstance(EventRegistry)

    replace(mockEventRegistry, 'store', storeStub as any)
    replace(mockEventRegistry, 'query', queryStub as any)
    replace(mockEventRegistry, 'queryLatest', queryLatestStub as any)
  })

  describe('readEntityEventsSince', () => {
    let mockEntityTypeName: string
    let mockEntityID: UUID

    beforeEach(() => {
      queryStub.resolves([mockEventEnvelop])

      mockEntityTypeName = random.alphaNumeric(10)
      mockEntityID = random.uuid()
    })

    it('should return expected result', async () => {
      const result = await readEntityEventsSince(
        mockEventRegistry,
        mockConfig,
        mockLogger,
        mockEntityTypeName,
        mockEntityID
      )

      expect(result).to.be.deep.equal([mockEventEnvelop])
    })

    context('date provided', () => {
      let dateStr: string

      beforeEach(async () => {
        dateStr = date.recent().toISOString()

        await readEntityEventsSince(
          mockEventRegistry,
          mockConfig,
          mockLogger,
          mockEntityTypeName,
          mockEntityID,
          dateStr
        )
      })

      it('should call event registry query with expected input', async () => {
        expect(queryStub).to.have.been.calledOnceWithExactly({
          createdAt: {
            $gt: dateStr,
          },
          entityID: mockEntityID,
          entityTypeName: mockEntityTypeName,
        })
      })
    })

    context('date not provided', () => {
      beforeEach(async () => {
        await readEntityEventsSince(mockEventRegistry, mockConfig, mockLogger, mockEntityTypeName, mockEntityID)
      })

      it('should call event registry query with expected input', () => {
        expect(queryStub).to.have.been.calledOnceWithExactly({
          createdAt: {
            $gt: new Date(0).toISOString(),
          },
          entityID: mockEntityID,
          entityTypeName: mockEntityTypeName,
        })
      })
    })
  })

  describe('readEntityLatestSnapshot', () => {
    let mockEntityTypeName: string
    let mockEntityID: UUID

    beforeEach(() => {
      queryLatestStub.resolves(mockSnapshot)

      mockEntityTypeName = random.alphaNumeric(10)
      mockEntityID = random.uuid()
    })

    it('should call event registry queryLatest', async () => {
      await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockLogger, mockEntityTypeName, mockEntityID)

      expect(queryLatestStub).to.have.been.calledOnceWithExactly({
        entityID: mockEntityID,
        entityTypeName: mockEntityTypeName,
        kind: 'snapshot',
      })
    })

    context('with snapshot', () => {
      beforeEach(() => {
        queryLatestStub.resolves(mockSnapshot)
      })

      it('should log expected message', async () => {
        await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockLogger, mockEntityTypeName, mockEntityID)

        expect(loggerDebugStub).to.have.been.calledOnceWithExactly(
          `[EventsAdapter#readEntityLatestSnapshot] Snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}:`,
          mockSnapshot
        )
      })

      it('should return expected result', async () => {
        const result = await readEntityLatestSnapshot(
          mockEventRegistry,
          mockConfig,
          mockLogger,
          mockEntityTypeName,
          mockEntityID
        )

        expect(result).to.be.deep.equal(mockSnapshot)
      })
    })

    context('without snapshot', () => {
      beforeEach(async () => {
        queryLatestStub.resolves(undefined)
      })

      it('should log expected message', async () => {
        await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockLogger, mockEntityTypeName, mockEntityID)

        expect(loggerDebugStub).to.have.been.calledOnceWithExactly(
          `[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}.`
        )
      })

      it('should return null', async () => {
        const result = await readEntityLatestSnapshot(
          mockEventRegistry,
          mockConfig,
          mockLogger,
          mockEntityTypeName,
          mockEntityID
        )

        expect(result).to.be.deep.equal(null)
      })
    })
  })

  describe('storeEvents', () => {
    context('no event envelopes', () => {
      beforeEach(async () => {
        await storeEvents(mockUserApp, mockEventRegistry, [], mockConfig, mockLogger)
      })

      it('should not call event registry store', () => {
        expect(storeStub).not.to.have.been.called
      })

      it('should call userApp boosterEventDispatcher', () => {
        expect(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([])
      })
    })

    context('with event envelopes', () => {
      let mockEventEnvelop: EventEnvelope

      beforeEach(async () => {
        mockEventEnvelop = createMockEventEnvelop()

        await storeEvents(mockUserApp, mockEventRegistry, [mockEventEnvelop], mockConfig, mockLogger)
      })

      it('should call event registry store', () => {
        expect(storeStub).to.have.been.calledWithExactly(mockEventEnvelop)
      })

      it('should call userApp boosterEventDispatcher', () => {
        expect(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([mockEventEnvelop])
      })
    })
  })
})
