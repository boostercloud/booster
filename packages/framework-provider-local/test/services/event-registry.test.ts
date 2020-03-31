/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary, EventEnvelope } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as sinonChai from 'sinon-chai'
import { EventRegistry } from '../../src/services/event-registry'
chai.use(chaiAsPromised)
chai.use(sinonChai)

describe('the event registry', () => {
  beforeEach(() => {
    restore()
  })
  const provider = {} as ProviderLibrary
  const config = new BoosterConfig()
  config.provider = provider

  describe('the publish method', () => {
    it('should insert events into the events database', async () => {
      const eventRegistry = new EventRegistry()
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

      eventRegistry.events.insert = stub().yields(null, event)

      await eventRegistry.publish(event)
      return expect(eventRegistry.events.insert).to.have.been.called
    })

    it('should throw if the database `insert` fails', async () => {
      const eventRegistry = new EventRegistry()
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

      return expect(eventRegistry.publish(event)).to.be.rejectedWith(error)
    })
  })
})
