/* eslint-disable @typescript-eslint/no-explicit-any */
import { EventEnvelope } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import { EventRegistry } from '../../src/services'
import { createMockEventEnvelop } from '../helpers/event-helper'
import { date, random } from 'faker'

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
    beforeEach(async () => {
      const publishPromises: Array<Promise<any>> = []

      for (let i = 0; i < initialEventsCount; i++) {
        publishPromises.push(eventRegistry.store(createMockEventEnvelop()))
      }

      await Promise.all(publishPromises)

      mockTargetEvent = createMockEventEnvelop()
      await eventRegistry.store(mockTargetEvent)
    })

    it('should return expected event', async () => {
      const result = await eventRegistry.query({
        kind: mockTargetEvent.kind,
        entityID: mockTargetEvent.entityID,
        entityTypeName: mockTargetEvent.entityTypeName,
        value: mockTargetEvent.value,
        createdAt: mockTargetEvent.createdAt,
        requestID: mockTargetEvent.requestID,
        typeName: mockTargetEvent.typeName,
        version: mockTargetEvent.version,
      })

      expect(result.length).to.be.equal(1)
      expect(result[0]).to.deep.include(mockTargetEvent)
    })
  })

  describe('query latest', () => {
    let copyOfMockTargetEvent: EventEnvelope
    let newerMockDate: string

    beforeEach(async () => {
      mockTargetEvent = createMockEventEnvelop()
      await eventRegistry.store(mockTargetEvent)

      newerMockDate = date.recent().toISOString()
      copyOfMockTargetEvent = {
        ...mockTargetEvent,
        createdAt: newerMockDate,
      }
      await eventRegistry.store(copyOfMockTargetEvent)
    })

    it('should return latest item', async () => {
      const result: EventEnvelope = await eventRegistry.queryLatest({
        kind: mockTargetEvent.kind,
        entityID: mockTargetEvent.entityID,
        entityTypeName: mockTargetEvent.entityTypeName,
      })

      expect(result).to.deep.include(copyOfMockTargetEvent)
    })
  })

  describe('delete all', () => {
    beforeEach(async () => {
      const mockEvent: EventEnvelope = createMockEventEnvelop()
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
      const mockEvent: EventEnvelope = createMockEventEnvelop()

      eventRegistry.events.insert = stub().yields(null, mockEvent)

      await eventRegistry.store(mockEvent)
      return expect(eventRegistry.events.insert).to.have.been.called
    })

    it('should throw if the database `insert` fails', async () => {
      const event: EventEnvelope = {
        kind: 'event',
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
