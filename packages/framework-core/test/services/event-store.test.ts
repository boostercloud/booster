/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe } from 'mocha'
import {
  BoosterConfig,
  EventEnvelope,
  EventInterface,
  EntityInterface,
  Level,
  ProviderLibrary,
  UUID,
} from '@boostercloud/framework-types'
import { replace, fake, stub, restore } from 'sinon'
import { EventStore } from '../../src/services/event-store'
import { expect } from '../expect'
import { BoosterEntityMigrated } from '../../dist/core-concepts/data-migration/events/booster-entity-migrated'

describe('EventStore', () => {
  afterEach(() => {
    restore()
  })
  const testConfig = new BoosterConfig('Test')
  testConfig.logLevel = Level.error

  class AnEvent {
    public constructor(readonly id: UUID, readonly entityId: string, readonly delta: number) {}
    public entityID(): UUID {
      return this.entityId
    }
  }

  class AnotherEvent {
    public constructor(readonly id: UUID) {}
    public entityID(): UUID {
      return this.id
    }
    public getPrefixedId(prefix: string): string {
      return `${prefix}-${this.id}`
    }
  }

  class AnEntity {
    public constructor(readonly id: UUID, readonly count: number) {}
    public getId(): UUID {
      return this.id
    }
    public static reducerThatCallsEntityMethod(event: AnEvent, currentEntity?: AnEntity): AnEntity {
      if (currentEntity) {
        currentEntity.getId()
      }
      return new AnEntity(event.entityId, event.delta)
    }

    public static reducerThatCallsEventMethod(event: AnotherEvent, currentEntity?: AnEntity): AnEntity {
      event.getPrefixedId('prefix')
      return new AnEntity('1', 1)
    }
  }

  const config = new BoosterConfig('test')
  config.provider = {
    events: {
      store: () => {},
      latestEntitySnapshot: () => {},
      forEntitySince: () => {},
    },
  } as any as ProviderLibrary
  config.entities[AnEntity.name] = { class: AnEntity, authorizeReadEvents: [] }
  config.reducers[AnEvent.name] = {
    class: AnEntity,
    methodName: 'reducerThatCallsEntityMethod',
  }
  config.reducers[AnotherEvent.name] = {
    class: AnEntity,
    methodName: 'reducerThatCallsEventMethod',
  }
  config.events[AnEvent.name] = { class: AnEvent }
  config.events[AnotherEvent.name] = { class: AnotherEvent }

  const importantDateTimeStamp = new Date(2019, 11, 23, 6, 30).toISOString()
  const originOfTime = new Date(0).toISOString() // Unix epoch

  const someEvent = {
    id: '1',
    entityID: () => '42',
    entityId: '42',
    delta: 1,
  }

  const otherEvent = {
    entityID: () => '42',
    entityId: '42',
    delta: 2,
  }

  const someEntity = {
    id: '42',
    count: 0,
  }

  function eventEnvelopeFor<TEvent extends EventInterface>(
    event: TEvent,
    typeName: string,
    timestamp?: string
  ): EventEnvelope {
    return {
      version: 1,
      kind: 'event',
      superKind: 'domain',
      entityID: '42',
      entityTypeName: AnEntity.name,
      value: event,
      requestID: 'whatever',
      typeName: typeName,
      createdAt: timestamp || new Date().toISOString(),
    }
  }

  function snapshotEnvelopeFor<TEntity extends EntityInterface>(entity: TEntity): EventEnvelope {
    return {
      version: 1,
      kind: 'snapshot',
      superKind: 'domain',
      entityID: '42',
      entityTypeName: AnEntity.name,
      value: entity,
      requestID: 'whatever',
      typeName: AnEntity.name,
      createdAt: 'fakeTimeStamp',
      snapshottedEventCreatedAt: importantDateTimeStamp,
    }
  }

  describe('public methods', () => {
    describe('fetchEntitySnapshot', () => {
      it('properly binds `this` to the entityReducer', async () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const eventStore = new EventStore(config) as any
        const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)

        replace(eventStore, 'loadLatestSnapshot', fake.resolves(null))
        replace(eventStore, 'loadEventStreamSince', fake.resolves([someEventEnvelope]))
        replace(eventStore, 'entityReducer', function () {
          // @ts-ignore
          expect(this).to.be.equal(eventStore)
        })

        const entityName = AnEntity.name
        const entityID = '42'
        await expect(eventStore.fetchEntitySnapshot(entityName, entityID)).to.be.eventually.fulfilled
      })

      context('when there is a snapshot but no pending events', () => {
        it('returns the snapshot', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any
          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)

          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))
          replace(eventStore, 'loadEventStreamSince', fake.resolves([]))
          replace(eventStore, 'entityReducer', fake())
          replace(eventStore, 'storeSnapshot', fake())

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(
            entityName,
            entityID,
            importantDateTimeStamp
          )
          expect(eventStore.entityReducer).not.to.have.been.called
          expect(eventStore.storeSnapshot).not.to.have.been.called

          expect(entity).to.be.deep.equal(snapshotEnvelopeFor(someEntity))
        })
      })

      context('when there is a snapshot and a short list of pending events', () => {
        it('produces and returns a new snapshot without storing it', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any
          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name)

          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))
          replace(eventStore, 'loadEventStreamSince', fake.resolves([someEventEnvelope, otherEventEnvelope]))

          const reducer = stub()
            .onFirstCall()
            .returns(
              snapshotEnvelopeFor({
                id: '42',
                count: 1,
              })
            )
            .onSecondCall()
            .returns(
              snapshotEnvelopeFor({
                id: '42',
                count: 3,
              })
            )
          replace(eventStore, 'entityReducer', reducer)
          replace(eventStore, 'storeSnapshot', fake())

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(
            entityName,
            entityID,
            importantDateTimeStamp
          )

          expect(eventStore.entityReducer.firstCall.args[0]).to.deep.equal(someSnapshotEnvelope)
          expect(eventStore.entityReducer.firstCall.args[1]).to.deep.equal(someEventEnvelope)
          expect(eventStore.entityReducer.secondCall.args[0]).to.deep.equal(
            snapshotEnvelopeFor({
              id: '42',
              count: 1,
            })
          )
          expect(eventStore.entityReducer.secondCall.args[1]).to.deep.equal(otherEventEnvelope)

          expect(eventStore.storeSnapshot).not.to.have.been.called

          expect(entity).to.be.deep.equal(
            snapshotEnvelopeFor({
              id: '42',
              count: 3,
            })
          )
        })
      })

      context('when there is a snapshot and a long list of pending events', () => {
        it('produces a new snapshot and returns it, but never stores it', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any
          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name)
          const pendingEvents = [
            someEventEnvelope,
            otherEventEnvelope,
            someEventEnvelope,
            otherEventEnvelope,
            someEventEnvelope,
            otherEventEnvelope,
          ]
          const results = [1, 3, 4, 6, 7, 9]
          const inputs = [someSnapshotEnvelope].concat(
            results.map((result) => {
              return snapshotEnvelopeFor({
                id: '42',
                count: result,
              })
            })
          )

          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const reducer = stub()

          results.forEach((result, index) => {
            reducer.onCall(index).returns(
              snapshotEnvelopeFor({
                id: '42',
                count: result,
              })
            )
          })

          replace(eventStore, 'entityReducer', reducer)
          replace(eventStore, 'storeSnapshot', fake())

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(
            entityName,
            entityID,
            importantDateTimeStamp
          )

          for (let index = 0; index < results.length; index++) {
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(inputs[index])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(pendingEvents[index])
          }

          expect(eventStore.storeSnapshot).to.not.have.been.called

          expect(entity).to.be.deep.equal(
            snapshotEnvelopeFor({
              id: '42',
              count: 9,
            })
          )
        })
      })

      context('with no snapshot and a list of more than 5 events', () => {
        it('produces a new snapshot and returns it, but never stores it', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name)
          const pendingEvents = [
            someEventEnvelope,
            otherEventEnvelope,
            someEventEnvelope,
            otherEventEnvelope,
            someEventEnvelope,
            otherEventEnvelope,
          ]
          const results = [1, 3, 4, 6, 7, 9]
          const inputs = results.map((result) => {
            return snapshotEnvelopeFor({
              id: '42',
              count: result,
            })
          })

          replace(eventStore, 'loadLatestSnapshot', fake.resolves(null))
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const reducer = stub()

          results.forEach((result, index) => {
            reducer.onCall(index).returns(
              snapshotEnvelopeFor({
                id: '42',
                count: result,
              })
            )
          })

          replace(eventStore, 'entityReducer', reducer)
          replace(eventStore, 'storeSnapshot', fake())

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime)

          expect(eventStore.entityReducer.getCall(0).args[0]).to.be.null
          expect(eventStore.entityReducer.getCall(0).args[1]).to.deep.equal(pendingEvents[0])
          for (let index = 1; index < results.length; index++) {
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(inputs[index - 1])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(pendingEvents[index])
          }

          expect(eventStore.storeSnapshot).to.not.have.been.called

          expect(entity).to.be.deep.equal(
            snapshotEnvelopeFor({
              id: '42',
              count: 9,
            })
          )
        })
      })

      context('with no snapshot and an empty list of events', () => {
        it('does nothing and returns null', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any

          replace(eventStore, 'loadLatestSnapshot', fake.resolves(null))
          replace(eventStore, 'loadEventStreamSince', fake.resolves([]))

          replace(eventStore, 'entityReducer', fake())
          replace(eventStore, 'storeSnapshot', fake())

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime)

          expect(eventStore.entityReducer).not.to.have.been.called
          expect(eventStore.storeSnapshot).not.to.have.been.called

          expect(entity).to.be.null
        })
      })
    })

    describe('storeSnapshot', () => {
      it('stores a snapshot in the event store', async () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const eventStore = new EventStore(config) as any
        replace(config.provider.events, 'store', fake())

        const someSnapshot = snapshotEnvelopeFor({
          id: '42',
          count: 666,
        })

        await eventStore.storeSnapshot(someSnapshot)

        expect(config.provider.events.store).to.have.been.calledOnceWith([someSnapshot], config)
      })
    })
  })

  describe('private methods', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const eventStore = new EventStore(config) as any

    describe('loadLatestSnapshot', () => {
      it('looks for the latest snapshot stored in the event stream', async () => {
        replace(config.provider.events, 'latestEntitySnapshot', fake())

        const entityTypeName = AnEntity.name
        const entityID = '42'
        await eventStore.loadLatestSnapshot(entityTypeName, entityID)

        expect(config.provider.events.latestEntitySnapshot).to.have.been.calledOnceWith(
          config,
          entityTypeName,
          entityID
        )
      })
    })

    describe('loadEventStreamSince', () => {
      it('loads a event stream starting from a specific timestamp', async () => {
        replace(config.provider.events, 'forEntitySince', fake())

        const entityTypeName = AnEntity.name
        const entityID = '42'
        await eventStore.loadEventStreamSince(entityTypeName, entityID, originOfTime)

        expect(config.provider.events.forEntitySince).to.have.been.calledOnceWith(
          config,
          entityTypeName,
          entityID,
          originOfTime
        )
      })
    })

    describe('entityReducer', () => {
      context('when an entity reducer has been registered for the event', () => {
        context('given a snapshot and a new event', () => {
          it('calculates the new snapshot value using the proper reducer for the event and the entity types', async () => {
            const snapshot = snapshotEnvelopeFor(someEntity)
            const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, 'fakeTimeStamp')
            const fakeReducer = fake.returns({
              id: '42',
              count: 1,
            })
            replace(eventStore, 'reducerForEvent', fake.returns(fakeReducer))

            const newSnapshot = await eventStore.entityReducer(snapshot, eventEnvelope)
            delete newSnapshot.createdAt

            const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta)
            eventInstance.entityID = someEvent.entityID
            const entityInstance = new AnEntity(someEntity.id, someEntity.count)

            expect(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name)
            expect(fakeReducer).to.have.been.calledOnceWith(eventInstance, entityInstance)

            expect(newSnapshot).to.be.deep.equal({
              version: 1,
              kind: 'snapshot',
              requestID: eventEnvelope.requestID,
              entityID: '42',
              entityTypeName: AnEntity.name,
              typeName: AnEntity.name,
              superKind: 'domain',
              value: {
                id: '42',
                count: 1,
              },
              snapshottedEventCreatedAt: 'fakeTimeStamp',
            })
          })
        })

        context('given no snapshot and an event', () => {
          it('generates a new snapshot value using the proper reducer for the event and the entity types', async () => {
            const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, 'fakeTimeStamp')
            const fakeReducer = fake.returns({
              id: '42',
              count: 1,
            })
            replace(eventStore, 'reducerForEvent', fake.returns(fakeReducer))

            const newSnapshot = await eventStore.entityReducer(null, eventEnvelope)
            delete newSnapshot.createdAt

            const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta)
            eventInstance.entityID = someEvent.entityID

            expect(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name)
            expect(fakeReducer).to.have.been.calledOnceWith(eventInstance, null)

            expect(newSnapshot).to.be.deep.equal({
              version: 1,
              kind: 'snapshot',
              requestID: eventEnvelope.requestID,
              entityID: '42',
              entityTypeName: AnEntity.name,
              typeName: AnEntity.name,
              superKind: 'domain',
              value: {
                id: '42',
                count: 1,
              },
              snapshottedEventCreatedAt: 'fakeTimeStamp',
            })
          })
        })

        context('given an internal event', () => {
          it('calculates the new internal snapshot', async () => {
            const snapshot = {}
            const eventEnvelope = {
              version: 1,
              kind: 'event',
              entityID: '42',
              entityTypeName: AnEntity.name,
              value: {
                oldEntityName: 'oldEntityName',
                oldEntityId: 'oldEntityId',
                newEntityName: 'newEntityName',
                newEntity: {
                  id: '42',
                },
              },
              requestID: 'whatever',
              typeName: BoosterEntityMigrated.name,
              superKind: 'booster',
              createdAt: 'fakeTimeStamp',
            }

            const newSnapshot = await eventStore.entityReducer(snapshot, eventEnvelope)
            delete newSnapshot.createdAt

            expect(newSnapshot).to.be.deep.equal({
              version: 1,
              kind: 'snapshot',
              requestID: eventEnvelope.requestID,
              entityID: '42',
              entityTypeName: 'newEntityName',
              typeName: 'newEntityName',
              superKind: 'booster',
              value: {
                id: '42',
              },
              snapshottedEventCreatedAt: 'fakeTimeStamp',
            })
          })
        })
      })
      context('when an entity reducer calls an instance method in the event', () => {
        it('is executed without failing', async () => {
          const eventEnvelope = eventEnvelopeFor(someEvent, AnotherEvent.name, 'fakeTimeStamp')
          const getIdFake = fake()
          replace(AnotherEvent.prototype, 'getPrefixedId', getIdFake)
          await eventStore.entityReducer(null, eventEnvelope)
          expect(getIdFake).to.have.been.called
        })
      })
      context('when an entity reducer calls an instance method in the entity', () => {
        it('is executed without failing', async () => {
          const snapshot = snapshotEnvelopeFor(someEntity)
          const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, 'fakeTimeStamp')
          const getIdFake = fake()
          replace(AnEntity.prototype, 'getId', getIdFake)
          await eventStore.entityReducer(snapshot, eventEnvelope)
          expect(getIdFake).to.have.been.called
        })
      })
    })

    describe('reducerForEvent', () => {
      context('for an event with a registered reducer', () => {
        it('returns the proper reducer method for the event', () => {
          const reducer = eventStore.reducerForEvent(AnEvent.name)

          expect(reducer).to.be.instanceOf(Function)
          expect(reducer).to.be.equal(eval('AnEntity')['reducerThatCallsEntityMethod'])
        })
      })

      context('for events without registered reducers', () => {
        it('fails miserably', () => {
          expect(() => eventStore.reducerForEvent('InventedEvent')).to.throw(
            /No reducer registered for event InventedEvent/
          )
        })
      })
    })
  })
})
