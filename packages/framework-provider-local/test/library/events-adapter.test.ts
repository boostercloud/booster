/* eslint-disable @typescript-eslint/no-explicit-any */
import { createStubInstance, fake, SinonStub, SinonStubbedInstance, replace, stub, restore } from 'sinon'
import { EventRegistry } from '../../src/services'
import {
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeEvents,
  rawEventsToEnvelopes,
} from '../../src/library/events-adapter'
import {
  UserApp,
  EventEnvelope,
  EntitySnapshotEnvelope,
  UUID,
  BoosterConfig,
  NonPersistedEventEnvelope,
} from '@boostercloud/framework-types'
import { expect } from '../expect'
import { createMockNonPersistedEventEnvelop, createMockEntitySnapshotEnvelope } from '../helpers/event-helper'
import { random, date } from 'faker'

describe('events-adapter', () => {
  let mockConfig: BoosterConfig
  let mockEventEnvelop: NonPersistedEventEnvelope
  let mockSnapshot: EntitySnapshotEnvelope

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

    mockEventEnvelop = createMockNonPersistedEventEnvelop()
    mockSnapshot = createMockEntitySnapshotEnvelope()

    loggerDebugStub = stub()
    storeStub = stub()
    boosterEventDispatcherStub = stub()
    queryStub = stub()
    queryLatestStub = stub()

    mockConfig.logger = {
      info: fake(),
      warn: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
    mockUserApp = {
      boosterEventDispatcher: boosterEventDispatcherStub,
    } as any
    mockEventRegistry = createStubInstance(EventRegistry)

    replace(mockEventRegistry, 'store', storeStub as any)
    replace(mockEventRegistry, 'query', queryStub as any)
    replace(mockEventRegistry, 'queryLatestSnapshot', queryLatestStub as any)
  })

  afterEach(() => {
    restore()
  })

  describe('rawEventsToEnvelopes', () => {
    it('should return an empty array of envelopes', async () => {
      const results = rawEventsToEnvelopes([])
      const expected: EventEnvelope[] = []
      expect(results).to.deep.equal(expected)
    })

    it('should return an array of envelopes', async () => {
      const event1: NonPersistedEventEnvelope = createMockNonPersistedEventEnvelop()
      const event2: NonPersistedEventEnvelope = createMockNonPersistedEventEnvelop()
      const rawEvents: unknown[] = [event1 as unknown, event2 as unknown]
      const results = rawEventsToEnvelopes(rawEvents)
      const expected: NonPersistedEventEnvelope[] = [event1, event2]
      expect(results).to.deep.equal(expected)
    })
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
      const result = await readEntityEventsSince(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)
      const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`

      expect(result).to.be.deep.equal([mockEventEnvelop])
      expect(mockConfig.logger?.debug).to.be.calledWith(
        '[Booster]|events-adapter#readEntityEventsSince: ',
        expectedLogMessage,
        [mockEventEnvelop]
      )
    })

    context('date provided', () => {
      let dateStr: string

      beforeEach(async () => {
        dateStr = date.recent().toISOString()

        await readEntityEventsSince(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID, dateStr)
      })

      it('should call event registry query with expected input', async () => {
        expect(queryStub).to.have.been.calledOnceWithExactly({
          createdAt: {
            $gt: dateStr,
          },
          kind: 'event',
          entityID: mockEntityID,
          entityTypeName: mockEntityTypeName,
          deletedAt: {
            $exists: false,
          },
        })
      })

      it('should call logger with message', async () => {
        const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`
        expect(mockConfig.logger?.debug).to.be.calledWith(
          '[Booster]|events-adapter#readEntityEventsSince: ',
          expectedLogMessage,
          [mockEventEnvelop]
        )
      })
    })

    context('date not provided', () => {
      beforeEach(async () => {
        await readEntityEventsSince(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)
      })

      it('should call event registry query with expected input', () => {
        expect(queryStub).to.have.been.calledOnceWithExactly({
          createdAt: {
            $gt: new Date(0).toISOString(),
          },
          kind: 'event',
          entityID: mockEntityID,
          entityTypeName: mockEntityTypeName,
          deletedAt: {
            $exists: false,
          },
        })
      })

      it('should call logger with message', async () => {
        const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`
        expect(mockConfig.logger?.debug).to.be.calledWith(
          '[Booster]|events-adapter#readEntityEventsSince: ',
          expectedLogMessage,
          [mockEventEnvelop]
        )
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
      await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)

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
        await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)

        expect(loggerDebugStub).to.have.been.calledOnceWithExactly(
          '[Booster]|events-adapter#readEntityLatestSnapshot: ',
          `Snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}:`,
          mockSnapshot
        )
      })

      it('should return expected result', async () => {
        const result = await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)

        expect(result).to.be.deep.equal(mockSnapshot)
      })
    })

    context('without snapshot', () => {
      beforeEach(async () => {
        queryLatestStub.resolves(null)
      })

      it('should log expected message', async () => {
        await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)

        expect(loggerDebugStub).to.have.been.calledOnceWithExactly(
          '[Booster]|events-adapter#readEntityLatestSnapshot: ',
          `No snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}.`
        )
      })

      it('should return null', async () => {
        const result = await readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID)

        expect(result).to.be.undefined
      })
    })
  })

  describe('storeEvents', () => {
    context('no event envelopes', () => {
      beforeEach(async () => {
        await storeEvents(mockUserApp, mockEventRegistry, [], mockConfig)
      })

      it('should not call event registry store', () => {
        expect(storeStub).not.to.have.been.called
      })

      it('should call userApp boosterEventDispatcher', () => {
        expect(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([])
      })
    })

    context('with event envelopes', () => {
      it('should call event registry store', async () => {
        const mockEventEnvelop = createMockNonPersistedEventEnvelop()
        // The `createdAt` will be set in the `persistEvent` method
        replace(Date.prototype, 'toISOString', () => 'a magical time')

        await storeEvents(mockUserApp, mockEventRegistry, [mockEventEnvelop], mockConfig)

        expect(storeStub).to.have.been.calledWithExactly({
          ...mockEventEnvelop,
          createdAt: 'a magical time',
        })
      })

      it('should call userApp boosterEventDispatcher', async () => {
        const mockEventEnvelop = createMockNonPersistedEventEnvelop()
        // The `createdAt` will be set in the `persistEvent` method
        replace(Date.prototype, 'toISOString', () => 'a magical time')

        await storeEvents(mockUserApp, mockEventRegistry, [mockEventEnvelop], mockConfig)

        expect(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([
          { ...mockEventEnvelop, createdAt: 'a magical time' },
        ])
      })
    })
  })
})
