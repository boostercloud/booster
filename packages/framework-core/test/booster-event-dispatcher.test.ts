/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { describe } from 'mocha'
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { fake, replace, restore, createStubInstance } from 'sinon'
import {
  BoosterConfig,
  Logger,
  EventEnvelope,
  UUID,
  EntityInterface,
  ProviderLibrary,
  Register,
  EventInterface,
} from '@boostercloud/framework-types'
import { expect } from './expect'
import { RawEventsParser } from '../src/services/raw-events-parser'
import { ReadModelStore } from '../src/services/read-model-store'
import { EventStore } from '../src/services/event-store'
import { RegisterHandler } from '../src/booster-register-handler'
import { random } from 'faker'

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
  createdAt: 'an uncertain future',
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
  createdAt: 'a few nanoseconds later',
}

describe('BoosterEventDispatcher', () => {
  afterEach(() => {
    restore()
  })

  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
  }

  const config = new BoosterConfig('test')
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
      it('waits for snapshotting and read model update process to complete', async () => {
        const stubEventStore = createStubInstance(EventStore)
        const stubReadModelStore = createStubInstance(ReadModelStore)

        const boosterEventDispatcher = BoosterEventDispatcher as any
        replace(boosterEventDispatcher, 'snapshotAndUpdateReadModels', fake())
        replace(boosterEventDispatcher, 'handleEvent', fake())

        const callback = boosterEventDispatcher.eventProcessor(stubEventStore, stubReadModelStore, logger)

        await callback(someEvent, config)

        expect(boosterEventDispatcher.snapshotAndUpdateReadModels).to.have.been.calledOnceWith(
          someEvent,
          stubEventStore,
          stubReadModelStore,
          logger
        )
      })

      it('waits for the event to be handled by the event handlers', async () => {
        const stubEventStore = createStubInstance(EventStore)
        const stubReadModelStore = createStubInstance(ReadModelStore)

        const boosterEventDispatcher = BoosterEventDispatcher as any
        replace(boosterEventDispatcher, 'snapshotAndUpdateReadModels', fake())
        replace(boosterEventDispatcher, 'handleEvent', fake())

        const callback = boosterEventDispatcher.eventProcessor(stubEventStore, stubReadModelStore, logger)

        await callback(someEvent, config)

        expect(boosterEventDispatcher.handleEvent).to.have.been.calledOnceWith(someEvent, config, logger)
      })
    })

    describe('the `snapshotAndUpdateReadModel` method', () => {
      it('gets the updated state for the event entity', async () => {
        const boosterEventDispatcher = BoosterEventDispatcher as any
        const eventStore = createStubInstance(EventStore)
        const readModelStore = createStubInstance(ReadModelStore)

        await boosterEventDispatcher.snapshotAndUpdateReadModels(someEvent, eventStore, readModelStore, logger)

        expect(eventStore.fetchEntitySnapshot).to.have.been.called
        expect(eventStore.fetchEntitySnapshot).to.have.been.calledOnceWith(someEvent.entityTypeName, someEvent.entityID)
      })

      it('projects the entity state to the corresponding read models', async () => {
        const boosterEventDispatcher = BoosterEventDispatcher as any
        const eventStore = createStubInstance(EventStore)
        eventStore.fetchEntitySnapshot = fake.resolves(someEntitySnapshot) as any

        const readModelStore = createStubInstance(ReadModelStore)

        await boosterEventDispatcher.snapshotAndUpdateReadModels(someEvent, eventStore, readModelStore, logger)

        expect(readModelStore.project).to.have.been.calledOnce
        expect(readModelStore.project).to.have.been.calledWith(someEntitySnapshot)
      })
    })

    describe('the `handleEvent` method', () => {
      afterEach(() => {
        config.eventHandlers['SomeEvent'] = []
      })

      it('does nothing and does not throw if there are no event handlers', async () => {
        replace(RegisterHandler, 'handle', fake())
        const boosterEventDispatcher = BoosterEventDispatcher as any
        // We try first with null array of event handlers
        config.eventHandlers['SomeEvent'] = null as any
        await boosterEventDispatcher.handleEvent(someEvent, config, logger)
        // And now with an empty array
        config.eventHandlers['SomeEvent'] = []
        await boosterEventDispatcher.handleEvent(someEvent, config, logger)
        // It should not throw any errors
      })

      it('calls all the handlers for the current event', async () => {
        const fakeHandler1 = fake()
        const fakeHandler2 = fake()
        config.eventHandlers['SomeEvent'] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventDispatcher = BoosterEventDispatcher as any
        await boosterEventDispatcher.handleEvent(someEvent, config, logger)

        expect(fakeHandler1).to.have.been.calledOnceWith(someEvent.value)
        expect(fakeHandler2).to.have.been.calledOnceWith(someEvent.value)
      })

      it('calls the register handler for all the published events', async () => {
        let capturedRegister1: Register = {} as any
        let capturedRegister2: Register = {} as any
        const fakeHandler1 = fake((event: EventInterface, register: Register) => {
          capturedRegister1 = register
        })
        const fakeHandler2 = fake((event: EventInterface, register: Register) => {
          capturedRegister2 = register
        })
        config.eventHandlers['SomeEvent'] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventDispatcher = BoosterEventDispatcher as any
        await boosterEventDispatcher.handleEvent(someEvent, config, logger)

        expect(RegisterHandler.handle).to.have.been.calledTwice
        expect(RegisterHandler.handle).to.have.been.calledWith(config, logger, capturedRegister1)
        expect(RegisterHandler.handle).to.have.been.calledWith(config, logger, capturedRegister2)
      })

      it('waits for async event handlers to finish', async () => {
        let capturedRegister: Register = new Register(random.uuid())
        const fakeHandler = fake(async (event: EventInterface, register: Register) => {
          await new Promise((resolve) => setTimeout(resolve, 100))
          register.events(someEvent.value as EventInterface)
          capturedRegister = register
        })
        config.eventHandlers['SomeEvent'] = [{ handle: fakeHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventDispatcher = BoosterEventDispatcher as any
        await boosterEventDispatcher.handleEvent(someEvent, config, logger)

        expect(RegisterHandler.handle).to.have.been.calledWith(config, logger, capturedRegister)
        expect(capturedRegister.eventList[0]).to.be.deep.equal(someEvent.value)
      })
    })
  })
})
