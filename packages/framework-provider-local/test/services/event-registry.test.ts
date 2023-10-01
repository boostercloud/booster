/* eslint-disable @typescript-eslint/no-explicit-any */
import { EventEnvelope, EntitySnapshotEnvelope } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import {
  createMockEventEnvelope,
  createMockEventEnvelopeForEntity,
  createMockEntitySnapshotEnvelope,
} from '../helpers/event-helper'
import { date, random } from 'faker'
import { EventRegistry } from '../../src/services'

describe('the event registry', () => {
  let initialEventsCount: number
  let mockTargetEvent: EventEnvelope

  let eventRegistry: EventRegistry

  beforeEach(async () => {
    initialEventsCount = random.number({ min: 2, max: 10 })
    eventRegistry = new EventRegistry()

    // Clear all events
    await eventRegistry.deleteAll()
  })

  afterEach(() => {
    restore()
  })

  describe('query', () => {
    describe('with db full of random events', () => {
      beforeEach(async () => {
        const publishPromises: Array<Promise<any>> = []

        for (let i = 0; i < initialEventsCount; i++) {
          publishPromises.push(eventRegistry.store(createMockEventEnvelope()))
        }

        await Promise.all(publishPromises)

        mockTargetEvent = createMockEventEnvelope()
        await eventRegistry.store(mockTargetEvent)
      })

      it('should return expected event', async () => {
        const result = (await eventRegistry.query({
          kind: mockTargetEvent.kind,
          entityID: mockTargetEvent.entityID,
          entityTypeName: mockTargetEvent.entityTypeName,
          value: mockTargetEvent.value,
          createdAt: mockTargetEvent.createdAt,
          requestID: mockTargetEvent.requestID,
          typeName: mockTargetEvent.typeName,
          version: mockTargetEvent.version,
        })) as Array<EventEnvelope>

        expect(result.length).to.be.equal(1)
        expect(result[0]).to.deep.include(mockTargetEvent)
      })
    })

    describe('with events of the same entity', () => {
      const entityName: string = random.word()
      const entityId: string = random.uuid()

      beforeEach(async () => {
        const publishPromises: Array<Promise<any>> = []

        for (let i = 0; i < initialEventsCount; i++) {
          publishPromises.push(eventRegistry.store(createMockEventEnvelopeForEntity(entityName, entityId)))
        }

        for (let i = 0; i < initialEventsCount; i++) {
          publishPromises.push(eventRegistry.store(createMockEventEnvelopeForEntity(entityName, random.uuid())))
        }

        for (let i = 0; i < initialEventsCount; i++) {
          publishPromises.push(eventRegistry.store(createMockEventEnvelope()))
        }

        await Promise.all(publishPromises)
      })

      it('should return expected events of the same id sorted', async () => {
        const result: EventEnvelope[] = (await eventRegistry.query({
          kind: 'event',
          entityID: entityId,
          entityTypeName: entityName,
        })) as Array<EventEnvelope>

        expect(result.length).to.be.equal(initialEventsCount)
        expect(result[0].entityID).to.be.equal(entityId)
        expect(result[0].entityTypeName).to.be.equal(entityName)
        expect(new Date(result[0].createdAt)).to.be.lessThan(new Date(result[result.length - 1].createdAt))
      })
    })
  })

  describe('query latest entity snapshot', () => {
    let mockTargetSnapshot: EntitySnapshotEnvelope
    let copyOfMockTargetSnapshot: EntitySnapshotEnvelope
    let newerMockDate: string

    beforeEach(async () => {
      mockTargetSnapshot = createMockEntitySnapshotEnvelope()
      await eventRegistry.store(mockTargetSnapshot)

      newerMockDate = date.recent().toISOString()
      copyOfMockTargetSnapshot = {
        ...mockTargetSnapshot,
        snapshottedEventCreatedAt: newerMockDate,
      }
      await eventRegistry.store(copyOfMockTargetSnapshot)
    })

    it('should return latest item', async () => {
      const result = await eventRegistry.queryLatestSnapshot({
        entityID: mockTargetSnapshot.entityID,
        entityTypeName: mockTargetSnapshot.entityTypeName,
      })

      expect(result).not.to.be.undefined
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { _id, ...rest } = result as any
      expect(rest).to.deep.equal(copyOfMockTargetSnapshot)
    })

    it('should return null', async () => {
      const result = await eventRegistry.queryLatestSnapshot({
        entityID: random.uuid(),
        entityTypeName: mockTargetSnapshot.entityTypeName,
      })

      expect(result).to.be.undefined
    })
  })

  describe('delete all', () => {
    beforeEach(async () => {
      const mockEvent: EventEnvelope = createMockEventEnvelope()
      await eventRegistry.store(mockEvent)
    })

    it('should clear all events', async () => {
      const numberOfDeletedEvents = await eventRegistry.deleteAll()

      expect(numberOfDeletedEvents).to.be.equal(1)
      expect(await eventRegistry.query({})).to.be.deep.equal([])
    })
  })

  describe('the publish method', () => {
    it('should insert events into the events database', async () => {
      const mockEvent: EventEnvelope = createMockEventEnvelope()

      eventRegistry.events.insert = stub().yields(null, mockEvent)

      await eventRegistry.store(mockEvent)
      return expect(eventRegistry.events.insert).to.have.been.called
    })

    it('should throw if the database `insert` fails', async () => {
      const event: EventEnvelope = {
        kind: 'event',
        superKind: 'domain',
        entityID: faker.random.uuid(),
        entityTypeName: faker.random.word(),
        value: {
          id: faker.random.uuid(),
        },
        createdAt: faker.date.past().toISOString(),
        requestID: faker.random.uuid(),
        typeName: faker.random.word(),
        version: faker.random.number(),
      }

      const error = new Error(faker.random.words())

      eventRegistry.events.insert = stub().yields(error, null)

      return expect(eventRegistry.store(event)).to.be.rejectedWith(error)
    })
  })
})
