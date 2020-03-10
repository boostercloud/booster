/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { describe } from 'mocha'
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { fake, replace, restore, createStubInstance, SinonStubbedInstance } from 'sinon'
import { BoosterConfig, Logger, EventEnvelope, UUID, EntityInterface } from '@boostercloud/framework-types'
import * as chai from 'chai'
import { expect } from 'chai'
import { RawEventsParser } from '../src/services/raw-events-parser'
import { EventStore } from '../src/services/event-store'
import { ReadModelStore } from '../src/services/read-model-store'
import { ProviderLibrary } from '@boostercloud/framework-types'

chai.use(require('sinon-chai'))

describe('BoosterEventDispatcher', () => {
  afterEach(() => {
    restore()
  })

  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
  }

  const config = new BoosterConfig()
  config.provider = {} as ProviderLibrary

  context('with a configured provider', () => {
    describe('the `dispatch` method', () => {
      it('calls the raw events parser once and processes all messages', async () => {
        replace(RawEventsParser, 'streamEvents', fake())

        const boosterEventDispatcher = BoosterEventDispatcher as any

        const rawEvents = [{ some: 'raw event' }, { some: 'other raw event' }]
        await boosterEventDispatcher.dispatch(rawEvents, config, logger)

        expect(RawEventsParser.streamEvents).to.have.been.calledWithMatch(
          config,
          rawEvents,
          boosterEventDispatcher.eventProcessor
        )
      })
    })

    describe('the `eventProcessor` method', () => {
      context('builds a callback method', () => {
        let callbackFunction: (event: EventEnvelope) => Promise<void>
        let stubEventStore: SinonStubbedInstance<EventStore>
        let stubReadModelStore: SinonStubbedInstance<ReadModelStore>

        const someEvent: EventEnvelope = {
          version: 1,
          kind: 'event',
          entityID: '42',
          entityTypeName: 'SomeEntity',
          value: {
            entityID: (): UUID => {
              return '42'
            },
          },
          requestID: '123',
          typeName: 'SomeEvent',
          createdAt: new Date().toISOString(),
        }

        const someEntity: EntityInterface = {
          id: '42',
        }

        const someEntitySnapshot: EventEnvelope = {
          version: 1,
          kind: 'snapshot',
          entityID: '42',
          entityTypeName: 'SomeEntity',
          value: someEntity,
          requestID: '234',
          typeName: 'SomeEntity',
          createdAt: new Date().toISOString(),
        }

        beforeEach(() => {
          stubEventStore = createStubInstance(EventStore)
          stubEventStore.fetchEntitySnapshot.resolves(someEntitySnapshot)
          stubReadModelStore = createStubInstance(ReadModelStore)

          const boosterEventDispatcher = BoosterEventDispatcher as any
          callbackFunction = boosterEventDispatcher.eventProcessor(stubEventStore, stubReadModelStore, logger)
        })

        it('appends an event to the event store', async () => {
          await callbackFunction(someEvent)

          expect(stubEventStore.append).to.have.been.calledOnceWith(someEvent)
        })

        it('gets the updated state for the event entity', async () => {
          await callbackFunction(someEvent)

          expect(stubEventStore.fetchEntitySnapshot).to.have.been.called
          expect(stubEventStore.fetchEntitySnapshot).to.have.been.calledOnceWith(
            someEvent.entityTypeName,
            someEvent.entityID
          )
        })

        it('projects the entity state to the corresponding read models', async () => {
          await callbackFunction(someEvent)

          expect(stubReadModelStore.project).to.have.been.called
          expect(stubReadModelStore.project).to.have.been.calledWithMatch(someEntitySnapshot)
        })
      })
    })
  })
})
