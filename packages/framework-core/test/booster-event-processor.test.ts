/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { createStubInstance, fake, match, replace, restore } from 'sinon'
import {
  BoosterConfig,
  EntityInterface,
  EntitySnapshotEnvelope,
  EventInterface,
  NonPersistedEventEnvelope,
  ProviderLibrary,
  Register,
  UUID,
} from '@boostercloud/framework-types'
import { expect } from './expect'
import { ReadModelStore } from '../src/services/read-model-store'
import { EventStore } from '../src/services/event-store'
import { RegisterHandler } from '../src/booster-register-handler'
import { random } from 'faker'
import { BoosterEventProcessor } from '../src/booster-event-processor'
import { PromisesError } from '@boostercloud/framework-common-helpers'

class SomeEvent {
  public constructor(readonly id: UUID) {}

  public entityID(): UUID {
    return this.id
  }

  public getPrefixedId(prefix: string): string {
    return `${prefix}-${this.id}`
  }
}

class SomeNotification {
  public constructor() {}
}

class AnEventHandler {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public static async handle(event: SomeEvent, register: Register): Promise<void> {
    event.getPrefixedId('prefix')
  }
}

const someEvent: NonPersistedEventEnvelope = {
  version: 1,
  kind: 'event',
  superKind: 'domain',
  entityID: '42',
  entityTypeName: 'SomeEntity',
  value: {
    entityID: (): UUID => {
      return '42'
    },
    id: '42',
  },
  requestID: '123',
  typeName: SomeEvent.name,
}

const someNotification: NonPersistedEventEnvelope = {
  version: 1,
  kind: 'event',
  superKind: 'domain',
  entityID: 'default',
  entityTypeName: 'defaultTopic',
  value: {},
  requestID: '123',
  typeName: SomeNotification.name,
}

const someEntity: EntityInterface = {
  id: '42',
}

const someEntitySnapshot: EntitySnapshotEnvelope = {
  version: 1,
  kind: 'snapshot',
  superKind: 'domain',
  entityID: '42',
  entityTypeName: 'SomeEntity',
  value: someEntity,
  requestID: '234',
  typeName: 'SomeEntity',
  createdAt: 'an uncertain future',
  persistedAt: 'a few nanoseconds later',
  snapshottedEventCreatedAt: 'an uncertain future',
}

describe('BoosterEventProcessor', () => {
  afterEach(() => {
    restore()
  })

  const config = new BoosterConfig('test')
  config.provider = {} as ProviderLibrary
  config.events[SomeEvent.name] = { class: SomeEvent }
  config.notifications[SomeNotification.name] = { class: SomeNotification }
  config.logger = {
    info: fake(),
    error: fake(),
    debug: fake(),
    warn: fake(),
  }

  context('with a configured provider', () => {
    describe('the `eventProcessor` method', () => {
      it('waits for the snapshot generation process and read model update process to complete', async () => {
        const stubEventStore = createStubInstance(EventStore)
        const stubReadModelStore = createStubInstance(ReadModelStore)

        const boosterEventProcessor = BoosterEventProcessor as any
        replace(boosterEventProcessor, 'snapshotAndUpdateReadModels', fake())
        replace(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', fake())
        replace(boosterEventProcessor, 'filterDispatched', fake.resolves([someEvent]))

        const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore)

        await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], config)

        expect(boosterEventProcessor.snapshotAndUpdateReadModels).to.have.been.calledOnceWith(
          config,
          someEvent.entityTypeName,
          someEvent.entityID,
          stubEventStore,
          stubReadModelStore
        )
      })

      it('waits for the event to be handled by the event handlers', async () => {
        const stubEventStore = createStubInstance(EventStore)
        const stubReadModelStore = createStubInstance(ReadModelStore)

        const boosterEventProcessor = BoosterEventProcessor as any
        const fakeFilterDispatched = fake.returns([someEvent])

        replace(boosterEventProcessor, 'filterDispatched', fakeFilterDispatched)
        replace(boosterEventProcessor, 'snapshotAndUpdateReadModels', fake())
        replace(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', fake())

        const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore)

        await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], config)

        expect(boosterEventProcessor.dispatchEntityEventsToEventHandlers).to.have.been.calledOnceWith(
          [someEvent],
          config
        )
      })

      it("doesn't call snapshotAndUpdateReadModels if the entity name is in config.topicToEvent", async () => {
        const stubEventStore = createStubInstance(EventStore)
        const stubReadModelStore = createStubInstance(ReadModelStore)

        const boosterEventProcessor = BoosterEventProcessor as any
        replace(boosterEventProcessor, 'snapshotAndUpdateReadModels', fake())
        replace(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', fake())

        const overriddenConfig = { ...config }
        overriddenConfig.topicToEvent = { [someEvent.entityTypeName]: 'SomeEvent' }

        const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore)

        await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], overriddenConfig)
        overriddenConfig.topicToEvent = {}

        expect(boosterEventProcessor.snapshotAndUpdateReadModels).not.to.have.been.called
      })
    })

    describe('the `snapshotAndUpdateReadModels` method', () => {
      it('gets the updated state for the event entity', async () => {
        const boosterEventProcessor = BoosterEventProcessor as any
        const eventStore = createStubInstance(EventStore)
        const readModelStore = createStubInstance(ReadModelStore)
        eventStore.fetchEntitySnapshot = fake.resolves({}) as any

        await boosterEventProcessor.snapshotAndUpdateReadModels(
          config,
          someEvent.entityTypeName,
          someEvent.entityID,
          eventStore,
          readModelStore
        )

        expect(eventStore.fetchEntitySnapshot).to.have.been.called
        expect(eventStore.fetchEntitySnapshot).to.have.been.calledOnceWith(someEvent.entityTypeName, someEvent.entityID)
      })

      it('projects the entity state to the corresponding read models', async () => {
        const boosterEventProcessor = BoosterEventProcessor as any
        const eventStore = createStubInstance(EventStore)
        eventStore.fetchEntitySnapshot = fake.resolves(someEntitySnapshot) as any

        const readModelStore = createStubInstance(ReadModelStore)

        await boosterEventProcessor.snapshotAndUpdateReadModels(
          config,
          someEvent.entityTypeName,
          someEvent.entityID,
          eventStore,
          readModelStore
        )
        expect(readModelStore.project).to.have.been.calledOnce
        expect(readModelStore.project).to.have.been.calledWith(someEntitySnapshot)
      })

      context('when the entity reduction fails', () => {
        it('logs the error, does not throw it, and the projects method is not called', async () => {
          const boosterEventProcessor = BoosterEventProcessor as any
          const eventStore = createStubInstance(EventStore)
          const readModelStore = createStubInstance(ReadModelStore)
          const error = new Error('some error')
          eventStore.fetchEntitySnapshot = fake.rejects(error) as any

          await expect(
            boosterEventProcessor.snapshotAndUpdateReadModels(
              config,
              someEvent.entityTypeName,
              someEvent.entityID,
              eventStore,
              readModelStore
            )
          ).to.be.eventually.fulfilled

          expect(readModelStore.project).not.to.have.been.called
          expect(config.logger?.error).to.have.been.calledWith(
            '[Booster]|BoosterEventDispatcher#snapshotAndUpdateReadModels: ',
            'Error while fetching or reducing entity snapshot:',
            error
          )
        })
      })
    })

    describe('the `dispatchEntityEventsToEventHandlers` method', () => {
      afterEach(() => {
        config.eventHandlers[SomeEvent.name] = []
      })

      it('does nothing and does not throw if there are no event handlers and no global handler', async () => {
        replace(RegisterHandler, 'handle', fake())
        const boosterEventProcessor = BoosterEventProcessor as any
        // We try first with null array of event handlers
        config.eventHandlers[SomeEvent.name] = null as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)
        // And now with an empty array
        config.eventHandlers[SomeEvent.name] = []
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)
        // It should not throw any errors
      })

      it('calls global handler for the current event if defined', async () => {
        const fakeGlobalHandler = fake()
        config.eventHandlers[SomeEvent.name] = [{ handle: fakeGlobalHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        const eventValue: any = someEvent.value
        const anEventInstance = new SomeEvent(eventValue.id)
        anEventInstance.entityID = eventValue.entityID

        expect(fakeGlobalHandler).to.have.been.calledOnceWith(anEventInstance)
      })

      it('calls all the handlers for the current event', async () => {
        const fakeHandler1 = fake()
        const fakeHandler2 = fake()
        const fakeGlobalHandler = fake()
        config.eventHandlers[SomeEvent.name] = [
          { handle: fakeHandler1 },
          { handle: fakeHandler2 },
          { handle: fakeGlobalHandler },
        ]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        const eventValue: any = someEvent.value
        const anEventInstance = new SomeEvent(eventValue.id)
        anEventInstance.entityID = eventValue.entityID

        expect(fakeHandler1).to.have.been.calledOnceWith(anEventInstance)
        expect(fakeHandler2).to.have.been.calledOnceWith(anEventInstance)
        expect(fakeGlobalHandler).to.have.been.calledOnceWith(anEventInstance)
      })

      it('calls all the handlers, even if the event is stored in the notifications field instead of the events one', async () => {
        const fakeHandler1 = fake()
        const fakeHandler2 = fake()
        const fakeGlobalHandler = fake()
        config.eventHandlers[SomeNotification.name] = [
          { handle: fakeHandler1 },
          { handle: fakeHandler2 },
          { handle: fakeGlobalHandler },
        ]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someNotification], config)

        const aNotificationInstance = new SomeNotification()

        expect(fakeHandler1).to.have.been.calledOnceWith(aNotificationInstance)
        expect(fakeHandler2).to.have.been.calledOnceWith(aNotificationInstance)
        expect(fakeGlobalHandler).to.have.been.calledOnceWith(aNotificationInstance)
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
        config.eventHandlers[SomeEvent.name] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        expect(RegisterHandler.handle).to.have.been.calledTwice
        expect(RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister1)
        expect(RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister2)
      })

      it('waits for async event handlers to finish', async () => {
        let capturedRegister: Register = new Register(random.uuid(), {} as any, RegisterHandler.flush)
        const fakeHandler = fake(async (event: EventInterface, register: Register) => {
          await new Promise((resolve) => setTimeout(resolve, 100))
          register.events(someEvent.value as EventInterface)
          capturedRegister = register
        })
        config.eventHandlers[SomeEvent.name] = [{ handle: fakeHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        expect(RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister)
        expect(capturedRegister.eventList[0]).to.be.deep.equal(someEvent.value)
      })

      it('continues processing other events when one event handler fails', async () => {
        const failingHandler = fake.rejects(new Error('Handler failed'))
        const successHandler = fake.resolves({})

        config.eventHandlers[SomeEvent.name] = [{ handle: failingHandler }]
        config.eventHandlers[SomeNotification.name] = [{ handle: successHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent, someNotification], config)

        expect(failingHandler).to.have.been.calledOnce
        expect(successHandler).to.have.been.calledOnce
        expect(config.logger?.error).to.have.been.calledWith(
          '[Booster]|BoosterEventDispatcher.dispatchEntityEventsToEventHandlers: ',
          match(/Failed to process handlers for event SomeEvent/)
        )
      })

      it('handles PromisesError from event handlers and logs failed reasons', async () => {
        const promisesError = new PromisesError([
          { status: 'rejected', reason: new Error('Handler 1 failed') },
          { status: 'rejected', reason: new Error('Handler 2 failed') },
        ])
        const failingHandler = fake.rejects(promisesError)

        config.eventHandlers[SomeEvent.name] = [{ handle: failingHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        expect(failingHandler).to.have.been.calledOnce
        expect(config.logger?.error).to.have.been.calledWith(
          '[Booster]|BoosterEventDispatcher.dispatchEntityEventsToEventHandlers: ',
          'Failed to process handlers for event SomeEvent:',
          match((value) => {
            return (
              Array.isArray(value) &&
              value.length === 1 &&
              Array.isArray(value[0].failedReasons) &&
              value[0].failedReasons.length === 2 &&
              value[0].failedReasons[0].message === 'Handler 1 failed' &&
              value[0].failedReasons[1].message === 'Handler 2 failed'
            )
          })
        )
      })

      it('processes all event handlers for an event even if one fails', async () => {
        const failingHandler = fake.rejects(new Error('Handler failed'))
        const successHandler = fake.resolves({})

        config.eventHandlers[SomeEvent.name] = [{ handle: failingHandler }, { handle: successHandler }]

        replace(RegisterHandler, 'handle', fake())

        const boosterEventProcessor = BoosterEventProcessor as any
        await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)

        expect(failingHandler).to.have.been.calledOnce
        expect(successHandler).to.have.been.calledOnce
        expect(config.logger?.error).to.have.been.called
      })
    })

    describe('the `filterDispatched` method', () => {
      it("removes events if they've been already dispatched", async () => {
        const boosterEventProcessor = BoosterEventProcessor as any
        const eventStore = createStubInstance(EventStore)
        const someEventEnvelope = { ...someEvent, id: 'event-id' }
        eventStore.storeDispatchedEvent = fake.returns(false) as any

        const eventsNotDispatched = await boosterEventProcessor.filterDispatched(
          config,
          [someEventEnvelope],
          eventStore
        )

        expect(eventStore.storeDispatchedEvent).to.have.been.called
        expect(eventStore.storeDispatchedEvent).to.have.been.calledOnceWith(someEventEnvelope)
        expect(eventsNotDispatched).to.deep.equal([])
        expect(config.logger?.warn).to.have.been.calledWith(
          '[Booster]|BoosterEventDispatcher#filterDispatched: ',
          'Event has already been dispatched. Skipping.',
          match.any
        )
      })
    })

    it('calls an instance method in the event and it is executed without failing', async () => {
      config.eventHandlers[SomeEvent.name] = [{ handle: AnEventHandler.handle }]
      const boosterEventProcessor = BoosterEventProcessor as any
      const getPrefixedIdFake = fake()
      replace(SomeEvent.prototype, 'getPrefixedId', getPrefixedIdFake)
      await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config)
      expect(getPrefixedIdFake).to.have.been.called
    })
  })
})
