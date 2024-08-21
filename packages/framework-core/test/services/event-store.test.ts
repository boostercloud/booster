/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
import { describe } from 'mocha'
import {
  BOOSTER_SUPER_KIND,
  BoosterConfig,
  EntityInterface,
  EventEnvelope,
  EventInterface,
  Level,
  NonPersistedEntitySnapshotEnvelope,
  ProviderLibrary,
  UUID,
} from '@boostercloud/framework-types'
import { fake, replace, restore, stub, match, spy } from 'sinon'
import { EventStore } from '../../src/services/event-store'
import { expect } from '../expect'
import { BoosterEntityMigrated } from '../../src/core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterAuthorizer } from '../../src/booster-authorizer'

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

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    public static reducerThatCallsEventMethod(event: AnotherEvent, currentEntity?: AnEntity): AnEntity {
      event.getPrefixedId('prefix')
      return new AnEntity('1', 1)
    }
  }

  const config = new BoosterConfig('test')
  config.provider = {
    events: {
      storeSnapshot: () => {},
      latestEntitySnapshot: () => {},
      forEntitySince: () => {},
    },
  } as any as ProviderLibrary
  config.entities[AnEntity.name] = {
    class: AnEntity,
    eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
  }
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
  config.logger = {
    info: fake(),
    debug: fake(),
    error: fake(),
    warn: fake(),
  }

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
    timestamp: Date = new Date()
  ): EventEnvelope {
    const getEntityID = event.entityID ?? (() => '')
    const createdAt = timestamp.toISOString()
    return {
      version: 1,
      kind: 'event',
      superKind: 'domain',
      entityID: getEntityID(),
      entityTypeName: AnEntity.name,
      value: event,
      requestID: 'whatever',
      typeName: typeName,
      createdAt,
    }
  }

  function snapshotEnvelopeFor<TEntity extends EntityInterface>(entity: TEntity): NonPersistedEntitySnapshotEnvelope {
    return {
      version: 1,
      kind: 'snapshot',
      superKind: 'domain',
      entityID: entity.id,
      entityTypeName: AnEntity.name,
      value: entity,
      requestID: 'whatever',
      typeName: AnEntity.name,
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
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
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
        it('produces and returns a new snapshot, storing it', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any
          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          const someEventEnvelopePersistedAt = new Date()
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, someEventEnvelopePersistedAt)
          const otherEventEnvelopePersistedAt = new Date(someEventEnvelopePersistedAt.getTime() + 100) // 100ms later
          const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name, otherEventEnvelopePersistedAt)

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
          const expectedResult = {
            ...snapshotEnvelopeFor({
              id: '42',
              count: 3,
            }),
            createdAt: importantDateTimeStamp,
          }
          replace(eventStore, 'storeSnapshot', fake.resolves(expectedResult))

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(
            entityName,
            entityID,
            importantDateTimeStamp
          )

          expect(eventStore.entityReducer.firstCall.args[0]).to.deep.equal(someEventEnvelope)
          expect(eventStore.entityReducer.firstCall.args[1]).to.deep.equal(someSnapshotEnvelope)
          expect(eventStore.entityReducer.secondCall.args[0]).to.deep.equal(otherEventEnvelope)
          expect(eventStore.entityReducer.secondCall.args[1]).to.deep.equal(
            snapshotEnvelopeFor({
              id: '42',
              count: 1,
            })
          )

          expect(eventStore.storeSnapshot).to.have.been.called

          // Directly returns the value returned by storeSnapshot
          expect(entity).to.be.deep.equal(expectedResult)
        })
      })

      context('when there is a snapshot and a long list of pending events', () => {
        it('produces a new snapshot, stores it and returns it', async () => {
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
          const expectedResult = {
            ...snapshotEnvelopeFor({
              id: '42',
              count: 9,
            }),
            createdAt: importantDateTimeStamp,
          }
          replace(eventStore, 'storeSnapshot', fake.resolves(expectedResult))

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
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index])
          }

          expect(eventStore.storeSnapshot).to.have.been.called

          expect(entity).to.be.deep.equal(expectedResult)
        })
      })

      context('with no snapshot and a list of more than 5 events', () => {
        it('produces a new snapshot, stores it and returns it', async () => {
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
          const expectedResult = {
            ...snapshotEnvelopeFor({
              id: '42',
              count: 9,
            }),
            createdAt: importantDateTimeStamp,
          }
          replace(eventStore, 'storeSnapshot', fake.resolves(expectedResult))

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime)

          expect(eventStore.entityReducer.getCall(0).args[0]).to.deep.equal(pendingEvents[0])
          expect(eventStore.entityReducer.getCall(0).args[1]).to.be.null
          for (let index = 1; index < results.length; index++) {
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index - 1])
          }

          expect(eventStore.storeSnapshot).to.have.been.called

          expect(entity).to.be.deep.equal(expectedResult)
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

      context('with a stream that contains BEM events', () => {
        it('returns the reduced snapshot including the changes from BEM events', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any

          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))

          const fakeEntityReducer = stub()
          const reducersIds = ['42', '90', '42', '91', '42', '92'] // BEM events could return a different ID
          const reducersCount = [0, 1, 3, 4, 6, 7]
          reducersCount.forEach((result, index) => {
            fakeEntityReducer.onCall(index).returns(
              snapshotEnvelopeFor({
                id: reducersIds[index],
                count: result,
              })
            )
          })
          replace(eventStore, 'entityReducer', fakeEntityReducer)
          replace(eventStore, 'storeSnapshot', fake())

          // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const bemEventsEnvelopes = ['90', '91', '92'].map((id) => {
            return eventEnvelopeFor(
              {
                entityID: () => '42', // BEM events will return oldEntityId
                entityId: 42,
                delta: 2,
                superKind: BOOSTER_SUPER_KIND,
                newEntity: {
                  id: id,
                },
              },
              'bemEvent'
            )
          })
          const pendingEvents = [
            someEventEnvelope,
            bemEventsEnvelopes[0], // BEM event for entityID = 90
            someEventEnvelope,
            bemEventsEnvelopes[1], // BEM event for entityID = 91
            someEventEnvelope,
            bemEventsEnvelopes[2], // BEM event for entityID = 92
          ]
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const entityName = AnEntity.name
          const entityID = '42'
          await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)

          const expectedCounts = [0, 0, 1, 3, 4, 6] // should call with updated snapshot from entityID = 42
          const expectedReturnedIDs = ['42', '42', '90', '42', '91', '42'] // should call with updated snapshot from entityID = 42
          const expectedSnapshotArguments = expectedCounts.map((expectedCount, index) => {
            return snapshotEnvelopeFor({
              id: expectedReturnedIDs[index],
              count: expectedCount,
            })
          })

          for (let index = 0; index < reducersCount.length; index++) {
            const expectedSnapshotArgument = expectedSnapshotArguments[index]
            const expectedEventArgument = pendingEvents[index]
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(expectedEventArgument)
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(expectedSnapshotArgument)
          }

          const expectedFinalSnapshot = snapshotEnvelopeFor({
            id: '92',
            count: 7,
          })
          expect(eventStore.storeSnapshot).to.have.been.calledOnceWith(expectedFinalSnapshot)
        })
      })

      context('when a reducer throws an exception', () => {
        it('the process for the entity is halted and a `ReducerError` exception is raised', async () => {
          const eventStore = new EventStore(config) as any

          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))

          const reducer = stub()
          const reducersIds = ['42', '90', '42', '91', '42', '92'] // BEM events could return a different ID
          for (let index = 0; index < 6; index++) {
            if (index === 2) {
              reducer.onCall(index).rejects(new Error('Error on reducer'))
            } else {
              reducer.onCall(index).returns(
                snapshotEnvelopeFor({
                  id: reducersIds[index],
                  count: index + 1,
                })
              )
            }
          }
          replace(eventStore, 'entityReducer', reducer)

          replace(eventStore, 'storeSnapshot', fake())

          // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
          const someEventEnvelopes = [1, 3, 5].map((delta) => eventEnvelopeFor({ ...someEvent, delta }, AnEvent.name))
          const bemEventsEnvelopes = ['90', '91', '92'].map((id, index) => {
            return eventEnvelopeFor(
              {
                entityID: () => '42', // BEM events will return oldEntityId
                entityId: 42,
                delta: 2 * (index + 1),
                superKind: BOOSTER_SUPER_KIND,
                newEntity: {
                  id: id,
                },
              },
              'bemEvent'
            )
          })
          const pendingEvents = [
            someEventEnvelopes[0],
            bemEventsEnvelopes[0],
            someEventEnvelopes[1],
            bemEventsEnvelopes[1],
            someEventEnvelopes[2],
            bemEventsEnvelopes[2],
          ]
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const entityName = AnEntity.name
          const entityID = '42'
          await expect(eventStore.fetchEntitySnapshot(entityName, entityID)).to.eventually.be.rejectedWith(
            'Error on reducer'
          )

          expect(eventStore.entityReducer).to.have.been.calledThrice
          expect(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[0], someSnapshotEnvelope)
          expect(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[1], match.any)
          expect(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[2], match.any)
          expect(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[3], match.any)
          expect(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[4], match.any)
          expect(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[5], match.any)

          expect(eventStore.storeSnapshot).not.to.have.been.called
        })
      })

      context('when persisting the entity fails', () => {
        it('does not throw an exception and returns undefined', async () => {
          //eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any

          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          replace(eventStore, 'loadLatestSnapshot', fake.resolves(someSnapshotEnvelope))

          const reducer = stub()
          const reducersReturnIds = ['42', '90', '42', '91', '42', '92'] // BEM events could return a different ID
          const reducersReturnCount = [1, 2, 3, 4, 5, 6]
          reducersReturnCount.forEach((result, index) => {
            reducer.onCall(index).returns(
              snapshotEnvelopeFor({
                id: reducersReturnIds[index],
                count: result,
              })
            )
          })
          replace(eventStore, 'entityReducer', reducer)
          spy(eventStore, 'storeSnapshot')
          replace(config.provider.events, 'storeSnapshot', fake.rejects(new Error('Error on persist')))

          // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const bemEventsEnvelopes = ['90', '91', '92'].map((id) => {
            return eventEnvelopeFor(
              {
                entityID: () => '42', // BEM events will return oldEntityId
                entityId: 42,
                delta: 2,
                superKind: BOOSTER_SUPER_KIND,
                newEntity: {
                  id: id,
                },
              },
              'bemEvent'
            )
          })
          const pendingEvents = [
            someEventEnvelope,
            bemEventsEnvelopes[0],
            someEventEnvelope,
            bemEventsEnvelopes[1],
            someEventEnvelope,
            bemEventsEnvelopes[2],
          ]
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const entityName = AnEntity.name
          const entityID = '42'
          const entity = await eventStore.fetchEntitySnapshot(entityName, entityID)

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)

          const expectedSnapshotArguments = [someSnapshotEnvelope].concat(
            reducersReturnCount.map((expectedCount, index) => {
              return snapshotEnvelopeFor({
                id: reducersReturnIds[index],
                count: expectedCount,
              })
            })
          )
          for (let index = 0; index < reducersReturnCount.length; index++) {
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(expectedSnapshotArguments[index])
          }

          expect(eventStore.storeSnapshot).to.have.been.calledOnce
          expect(config.provider.events.storeSnapshot).to.have.been.calledOnce
          expect(entity).to.be.undefined
        })
      })

      // This could potentially happen if two or more processes are reading the same entity stream at the same time
      context('when there is a snapshot in the middle of the stream', () => {
        it('ignores the snapshot and continues processing the stream', async () => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const eventStore = new EventStore(config) as any

          // It doesn't initially see the snapshot
          replace(eventStore, 'loadLatestSnapshot', fake.resolves(null))

          const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity)
          const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name)
          const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name)
          const pendingEvents = [
            someEventEnvelope,
            otherEventEnvelope,
            someEventEnvelope,
            someSnapshotEnvelope, // But a snapshot appears in the middle of the stream
            otherEventEnvelope,
            someEventEnvelope,
            otherEventEnvelope,
          ]
          replace(eventStore, 'loadEventStreamSince', fake.resolves(pendingEvents))

          const results = [1, 3, 4, 6, 7, 9]
          const inputs = results.map((result) => {
            return snapshotEnvelopeFor({
              id: '42',
              count: result,
            })
          })

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
          await expect(eventStore.fetchEntitySnapshot(entityName, entityID)).to.eventually.be.fulfilled

          expect(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID)
          expect(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime)

          expect(eventStore.entityReducer.getCall(0).args[0]).to.deep.equal(pendingEvents[0])
          expect(eventStore.entityReducer.getCall(0).args[1]).to.be.null
          for (let index = 1; index < results.length; index++) {
            // skip the snapshot
            const eventIndex = index > 2 ? index + 1 : index
            expect(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[eventIndex])
            expect(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index - 1])
          }

          expect(eventStore.storeSnapshot).to.have.been.calledOnce
        })
      })
    })

    describe('storeSnapshot', () => {
      it('stores a snapshot in the event store', async () => {
        const eventStore = new EventStore(config) as any
        replace(config.provider.events, 'storeSnapshot', fake())

        const someSnapshot = snapshotEnvelopeFor({
          id: '42',
          count: 666,
        })

        await eventStore.storeSnapshot(someSnapshot)

        expect(config.provider.events.storeSnapshot).to.have.been.calledOnceWith(someSnapshot, config)
      })

      context('when there is an error storing the snapshot', () => {
        it('logs the error', async () => {
          const eventStore = new EventStore(config) as any
          const someSnapshot = snapshotEnvelopeFor({
            id: '42',
            count: 666,
          })
          const someError = new Error('some error')
          replace(config.provider.events, 'storeSnapshot', fake.rejects(someError))

          await eventStore.storeSnapshot(someSnapshot)

          expect(config.logger?.error).to.have.been.calledWithMatch(
            'EventStore#storeSnapshot',
            match.any,
            someSnapshot,
            '\nError:',
            someError
          )
        })
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
      it('loads a event stream starting from a specific timestapm', async () => {
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
            const fakeTime = new Date()
            const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime)
            const fakeReducer = fake.returns({
              id: '42',
              count: 1,
            })
            replace(eventStore, 'reducerForEvent', fake.returns(fakeReducer))

            const newSnapshot = await eventStore.entityReducer(eventEnvelope, snapshot)
            delete newSnapshot.createdAt

            const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta)
            eventInstance.entityID = someEvent.entityID
            const entityInstance = new AnEntity(someEntity.id, someEntity.count)

            expect(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name, eventInstance)
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
              snapshottedEventCreatedAt: fakeTime.toISOString(),
            })
          })
        })

        context('given no snapshot and an event', () => {
          it('generates a new snapshot value using the proper reducer for the event and the entity types', async () => {
            const fakeTime = new Date()
            const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime)
            const fakeReducer = fake.returns({
              id: '42',
              count: 1,
            })
            replace(eventStore, 'reducerForEvent', fake.returns(fakeReducer))

            const newSnapshot = await eventStore.entityReducer(eventEnvelope)
            delete newSnapshot.createdAt

            const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta)
            eventInstance.entityID = someEvent.entityID

            expect(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name, eventInstance)
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
              snapshottedEventCreatedAt: fakeTime.toISOString(),
            })
          })
        })

        context('given an internal event', () => {
          it('calculates the new internal snapshot', async () => {
            const snapshot = {}
            const fakeTime = new Date()
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
              createdAt: fakeTime.toISOString(),
            }

            const newSnapshot = await eventStore.entityReducer(eventEnvelope, snapshot)

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
              snapshottedEventCreatedAt: fakeTime.toISOString(),
            })
          })
        })
      })

      context('when an entity reducer calls an instance method in the event', () => {
        it('is executed without failing', async () => {
          const fakeTime = new Date()
          const eventEnvelope = eventEnvelopeFor(someEvent, AnotherEvent.name, fakeTime)
          const getIdFake = fake()
          replace(AnotherEvent.prototype, 'getPrefixedId', getIdFake)
          await eventStore.entityReducer(eventEnvelope)
          expect(getIdFake).to.have.been.called
        })
      })

      context('when an entity reducer calls an instance method in the entity', () => {
        it('is executed without failing', async () => {
          const snapshot = snapshotEnvelopeFor(someEntity)
          const fakeTime = new Date()
          const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime)
          const getIdFake = fake()
          replace(AnEntity.prototype, 'getId', getIdFake)
          await eventStore.entityReducer(eventEnvelope, snapshot)
          expect(getIdFake).to.have.been.called
        })
      })
    })

    describe('reducerForEvent', () => {
      context('for an event with a registered reducer', () => {
        it('returns the proper reducer method for the event', () => {
          const reducer = eventStore.reducerForEvent(AnEvent.name, {
            class: AnEntity,
            methodName: 'reducerThatCallsEntityMethod',
          })

          expect(reducer).to.be.instanceOf(Function)
          expect(reducer).to.be.equal(eval('AnEntity')['reducerThatCallsEntityMethod'])
        })
      })
    })
  })
})
