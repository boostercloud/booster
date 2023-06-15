/* eslint-disable @typescript-eslint/no-explicit-any */
import { beforeEach, describe } from 'mocha'
import { fake, replace, restore, SinonFakeTimers, spy, useFakeTimers } from 'sinon'
import { ReadModelStore } from '../../src/services/read-model-store'
import { createInstance } from '@boostercloud/framework-common-helpers'
import {
  BoosterConfig,
  EntitySnapshotEnvelope,
  Level,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionMetadata,
  ProjectionResult,
  ProviderLibrary,
  ReadModelAction,
  ReadModelInterface,
  UUID,
} from '@boostercloud/framework-types'
import { expect } from '../expect'
import { BoosterAuthorizer } from '../../src/booster-authorizer'

describe('ReadModelStore', () => {
  afterEach(() => {
    restore()
  })

  const testConfig = new BoosterConfig('Test')
  testConfig.logLevel = Level.error

  class AnImportantEntity {
    public constructor(readonly id: UUID, readonly someKey: UUID, readonly count: number) {}

    public getPrefixedKey(prefix: string): string {
      return `${prefix}-${this.someKey}`
    }
  }

  class AnImportantEntityWithArray {
    public constructor(readonly id: UUID, readonly someKey: Array<UUID>, readonly count: number) {}

    public getPrefixedKey(prefix: string): string {
      return `${prefix}-${this.someKey.join('-')}`
    }
  }

  class AnEntity {
    public constructor(readonly id: UUID, readonly someKey: UUID, readonly count: number) {}
  }

  class SomeReadModel {
    public constructor(readonly id: UUID) {}

    public static someObserver(entity: AnImportantEntity, obj: any): any {
      const count = (obj?.count || 0) + entity.count
      return { id: entity.someKey, kind: 'some', count: count }
    }

    public static someObserverArray(entity: AnImportantEntity, readModelID: UUID, obj: any): any {
      const count = (obj?.count || 0) + entity.count
      return { id: readModelID, kind: 'some', count: count }
    }

    public getId(): UUID {
      return this.id
    }

    public static projectionThatCallsReadModelMethod(
      entity: AnEntity,
      currentReadModel: SomeReadModel
    ): ProjectionResult<SomeReadModel> {
      currentReadModel.getId()
      return ReadModelAction.Nothing
    }

    public static projectionThatCallsEntityMethod(
      entity: AnImportantEntity,
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      currentReadModel: SomeReadModel
    ): ProjectionResult<SomeReadModel> {
      entity.getPrefixedKey('a prefix')
      return ReadModelAction.Nothing
    }
  }

  class AnotherReadModel {
    public constructor(readonly id: UUID) {}

    public static anotherObserver(entity: AnImportantEntity, obj: any): any {
      const count = (obj?.count || 0) + entity.count
      return { id: entity.someKey, kind: 'another', count: count }
    }
  }

  const config = new BoosterConfig('test')
  config.provider = {
    readModels: {
      store: () => {},
      delete: () => {},
      fetch: () => {},
    },
  } as unknown as ProviderLibrary
  config.entities[AnImportantEntity.name] = {
    class: AnImportantEntity,
    eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
  }
  config.entities[AnEntity.name] = {
    class: AnEntity,
    eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
  }
  config.entities[AnImportantEntityWithArray.name] = {
    class: AnImportantEntityWithArray,
    eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
  }
  config.readModels[SomeReadModel.name] = {
    class: SomeReadModel,
    authorizer: BoosterAuthorizer.allowAccess,
    properties: [],
    before: [],
  }
  config.readModels[AnotherReadModel.name] = {
    class: AnotherReadModel,
    authorizer: BoosterAuthorizer.allowAccess,
    properties: [],
    before: [],
  }
  config.projections[AnImportantEntity.name] = [
    {
      class: SomeReadModel,
      methodName: 'someObserver',
      joinKey: 'someKey',
    } as ProjectionMetadata<any>,
    {
      class: SomeReadModel,
      methodName: 'projectionThatCallsEntityMethod',
      joinKey: 'someKey',
    } as ProjectionMetadata<any>,
    {
      class: AnotherReadModel,
      methodName: 'anotherObserver',
      joinKey: 'someKey',
    } as ProjectionMetadata<any>,
  ]
  config.projections[AnImportantEntityWithArray.name] = [
    {
      class: SomeReadModel,
      methodName: 'someObserverArray',
      joinKey: 'someKey',
    } as ProjectionMetadata<any>,
  ]
  config.projections['AnEntity'] = [
    {
      class: SomeReadModel,
      methodName: 'projectionThatCallsReadModelMethod',
      joinKey: 'someKey',
    } as ProjectionMetadata<any>,
  ]

  function entitySnapshotEnvelopeFor(entityName: string): EntitySnapshotEnvelope {
    let someKeyValue: any = 'joinColumnID'
    if (AnImportantEntityWithArray.name == entityName) {
      someKeyValue = ['joinColumnID', 'anotherJoinColumnID']
    }
    const snapshottedEventCreatedAtDate = new Date()
    const snapshottedEventCreatedAt = snapshottedEventCreatedAtDate.toISOString()
    return {
      version: 1,
      kind: 'snapshot',
      superKind: 'domain',
      entityID: '42',
      entityTypeName: entityName,
      value: {
        id: 'importantEntityID',
        someKey: someKeyValue,
        count: 123,
      } as any,
      requestID: 'whatever',
      typeName: entityName,
      createdAt: snapshottedEventCreatedAt,
      persistedAt: new Date().toISOString(),
      snapshottedEventCreatedAt,
    }
  }

  describe('the `project` method', () => {
    context('when the entity class has no projections', () => {
      it('returns without errors and without performing any actions', async () => {
        const entitySnapshotWithNoProjections: EntitySnapshotEnvelope = {
          version: 1,
          kind: 'snapshot',
          superKind: 'domain',
          entityID: '42',
          entityTypeName: 'AConceptWithoutProjections',
          value: { entityID: () => '42' },
          requestID: 'whatever',
          typeName: AnImportantEntity.name,
          createdAt: new Date().toISOString(),
          persistedAt: new Date().toISOString(),
          snapshottedEventCreatedAt: new Date().toISOString(),
        }

        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config)
        replace(readModelStore, 'fetchReadModel', fake.returns(null))

        await expect(readModelStore.project(entitySnapshotWithNoProjections)).to.eventually.be.fulfilled

        expect(config.provider.readModels.store).not.to.have.been.called
        expect(readModelStore.fetchReadModel).not.to.have.been.called
      })
    })

    context('when the new read model returns ReadModelAction.Delete', () => {
      it('deletes the associated read model', async () => {
        replace(config.provider.readModels, 'store', fake())
        replace(config.provider.readModels, 'delete', fake())
        replace(
          ReadModelStore.prototype,
          'projectionFunction',
          fake.returns(() => ReadModelAction.Delete)
        )
        const readModelStore = new ReadModelStore(config)

        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name))
        expect(config.provider.readModels.store).not.to.have.been.called
        expect(config.provider.readModels.delete).to.have.been.calledThrice
      })
    })

    context('when the new read model returns ReadModelAction.Nothing', () => {
      it('ignores the read model', async () => {
        replace(config.provider.readModels, 'store', fake())
        replace(config.provider.readModels, 'delete', fake())
        replace(
          ReadModelStore.prototype,
          'projectionFunction',
          fake.returns(() => ReadModelAction.Nothing)
        )
        const readModelStore = new ReadModelStore(config)

        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name))
        expect(config.provider.readModels.store).not.to.have.been.called
        expect(config.provider.readModels.delete).not.to.have.been.called
      })
    })

    context("when the corresponding read models don't exist", () => {
      let clock: SinonFakeTimers
      before(() => {
        clock = useFakeTimers(0)
      })

      after(() => {
        clock.restore()
      })

      it('creates new instances of the read models', async () => {
        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config)
        replace(readModelStore, 'fetchReadModel', fake.returns(null))
        spy(SomeReadModel, 'someObserver')
        spy(AnotherReadModel, 'anotherObserver')
        const entityValue: any = entitySnapshotEnvelopeFor(AnImportantEntity.name).value
        const anEntityInstance = new AnImportantEntity(entityValue.id, entityValue.someKey, entityValue.count)

        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name))

        expect(readModelStore.fetchReadModel).to.have.been.calledThrice
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID')
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(AnotherReadModel.name, 'joinColumnID')
        expect(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntityInstance, null)
        expect(SomeReadModel.someObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'some',
          count: 123,
          boosterMetadata: {
            version: 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserver',
            },
          },
        })
        expect(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntityInstance, null)
        expect(AnotherReadModel.anotherObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'another',
          count: 123,
          boosterMetadata: {
            version: 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'AnotherReadModel.anotherObserver',
            },
          },
        })
        expect(config.provider.readModels.store).to.have.been.calledTwice
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          SomeReadModel.name,
          {
            id: 'joinColumnID',
            kind: 'some',
            count: 123,
            boosterMetadata: {
              version: 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntity',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'SomeReadModel.someObserver',
              },
            },
          },
          0
        )
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          AnotherReadModel.name,
          {
            id: 'joinColumnID',
            kind: 'another',
            count: 123,
            boosterMetadata: {
              version: 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntity',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'AnotherReadModel.anotherObserver',
              },
            },
          },
          0
        )
      })
    })

    context('when the corresponding read model did exist', () => {
      let clock: SinonFakeTimers
      before(() => {
        clock = useFakeTimers(0)
      })

      after(() => {
        clock.restore()
      })

      it('updates the read model', async () => {
        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config)
        const someReadModelStoredVersion = 10
        const anotherReadModelStoredVersion = 32
        replace(
          readModelStore,
          'fetchReadModel',
          fake((className: string, id: UUID) => {
            if (className == SomeReadModel.name) {
              return {
                id: id,
                kind: 'some',
                count: 77,
                boosterMetadata: {
                  version: someReadModelStoredVersion,
                  lastUpdateAt: '1970-01-01T00:00:00.000Z',
                  lastProjectionInfo: {
                    entityId: 'importantEntityID',
                    entityName: 'AnImportantEntity',
                    entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                    projectionMethod: 'SomeReadModel.someObserver',
                  },
                },
              }
            } else {
              return {
                id: id,
                kind: 'another',
                count: 177,
                boosterMetadata: {
                  version: anotherReadModelStoredVersion,
                  lastUpdateAt: '1970-01-01T00:00:00.000Z',
                  lastProjectionInfo: {
                    entityId: 'importantEntityID',
                    entityName: 'AnImportantEntity',
                    entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                    projectionMethod: 'AnotherReadModel.anotherObserver',
                  },
                },
              }
            }
          })
        )
        spy(SomeReadModel, 'someObserver')
        spy(AnotherReadModel, 'anotherObserver')
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const entityValue: any = anEntitySnapshot.value
        const anEntityInstance = new AnImportantEntity(entityValue.id, entityValue.someKey, entityValue.count)
        await readModelStore.project(anEntitySnapshot)

        expect(readModelStore.fetchReadModel).to.have.been.calledThrice
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID')
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(AnotherReadModel.name, 'joinColumnID')
        expect(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntityInstance, {
          id: 'joinColumnID',
          kind: 'some',
          count: 77,
          boosterMetadata: {
            version: someReadModelStoredVersion,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserver',
            },
          },
        })
        expect(SomeReadModel.someObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'some',
          count: 200,
          boosterMetadata: {
            version: someReadModelStoredVersion + 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserver',
            },
          },
        })
        expect(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntityInstance, {
          id: 'joinColumnID',
          kind: 'another',
          count: 177,
          boosterMetadata: {
            version: anotherReadModelStoredVersion,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'AnotherReadModel.anotherObserver',
            },
          },
        })
        expect(AnotherReadModel.anotherObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'another',
          count: 300,
          boosterMetadata: {
            version: anotherReadModelStoredVersion + 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntity',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'AnotherReadModel.anotherObserver',
            },
          },
        })
        expect(config.provider.readModels.store).to.have.been.calledTwice
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          SomeReadModel.name,
          {
            id: 'joinColumnID',
            kind: 'some',
            count: 200,
            boosterMetadata: {
              version: someReadModelStoredVersion + 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntity',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'SomeReadModel.someObserver',
              },
            },
          },
          someReadModelStoredVersion
        )
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          AnotherReadModel.name,
          {
            id: 'joinColumnID',
            kind: 'another',
            count: 300,
            boosterMetadata: {
              version: anotherReadModelStoredVersion + 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntity',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'AnotherReadModel.anotherObserver',
              },
            },
          },
          anotherReadModelStoredVersion
        )
      })
    })

    context('when the projection calls an instance method in the entity', () => {
      it('is executed without failing', async () => {
        const readModelStore = new ReadModelStore(config)
        const getPrefixedKeyFake = fake()
        replace(AnImportantEntity.prototype, 'getPrefixedKey', getPrefixedKeyFake)
        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name))
        expect(getPrefixedKeyFake).to.have.been.called
      })
    })

    context('when the projection calls an instance method in the read model', () => {
      it('is executed without failing', async () => {
        const readModelStore = new ReadModelStore(config)
        replace(config.provider.readModels, 'fetch', fake.returns([{ id: 'joinColumnID', count: 31415 }]))
        const getIdFake = fake()
        replace(SomeReadModel.prototype, 'getId', getIdFake)
        await readModelStore.project(entitySnapshotEnvelopeFor(AnEntity.name))
        expect(getIdFake).to.have.been.called
      })
    })

    context('when there is high contention and optimistic concurrency is needed', () => {
      let clock: SinonFakeTimers
      before(() => {
        clock = useFakeTimers(0)
      })

      after(() => {
        clock.restore()
      })

      it('retries 5 times when the error OptimisticConcurrencyUnexpectedVersionError happens 4 times', async () => {
        let tryNumber = 1
        const expectedTries = 5
        const fakeStore = fake((config: BoosterConfig, readModelName: string): Promise<unknown> => {
          if (readModelName === SomeReadModel.name && tryNumber < expectedTries) {
            tryNumber++
            throw new OptimisticConcurrencyUnexpectedVersionError('test error')
          }
          return Promise.resolve()
        })
        replace(config.provider.readModels, 'store', fakeStore)
        const readModelStore = new ReadModelStore(config)
        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name))

        const someReadModelStoreCalls = fakeStore.getCalls().filter((call) => call.args[1] === SomeReadModel.name)
        expect(someReadModelStoreCalls).to.be.have.length(expectedTries)
        someReadModelStoreCalls.forEach((call) => {
          expect(call.args).to.be.deep.equal([
            config,
            SomeReadModel.name,
            {
              id: 'joinColumnID',
              kind: 'some',
              count: 123,
              boosterMetadata: {
                version: 1,
                schemaVersion: 1,
                lastUpdateAt: '1970-01-01T00:00:00.000Z',
                lastProjectionInfo: {
                  entityId: 'importantEntityID',
                  entityName: 'AnImportantEntity',
                  entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                  projectionMethod: 'SomeReadModel.someObserver',
                },
              },
            },
            0,
          ])
        })
      })
    })

    context('when multiple read models are projected from Array joinKey', () => {
      let clock: SinonFakeTimers
      before(() => {
        clock = useFakeTimers(0)
      })

      after(() => {
        clock.restore()
      })

      it('creates non-existent read models and updates existing read models', async () => {
        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config)
        const someReadModelStoredVersion = 10
        replace(
          readModelStore,
          'fetchReadModel',
          fake((className: string, id: UUID) => {
            if (className == SomeReadModel.name) {
              if (id == 'anotherJoinColumnID') {
                return null
              } else {
                return {
                  id: id,
                  kind: 'some',
                  count: 77,
                  boosterMetadata: {
                    version: someReadModelStoredVersion,
                    lastUpdateAt: '1970-01-01T00:00:00.000Z',
                    lastProjectionInfo: {
                      entityId: 'importantEntityID',
                      entityName: 'AnImportantEntityWithArray',
                      entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                      projectionMethod: 'SomeReadModel.someObserverArray',
                    },
                  },
                }
              }
            }
            return null
          })
        )
        spy(SomeReadModel, 'someObserver')
        spy(SomeReadModel, 'someObserverArray')
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntityWithArray.name)
        const entityValue: any = anEntitySnapshot.value
        const anEntityInstance = new AnImportantEntityWithArray(entityValue.id, entityValue.someKey, entityValue.count)
        await readModelStore.project(anEntitySnapshot)

        expect(readModelStore.fetchReadModel).to.have.been.calledTwice
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID')
        expect(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'anotherJoinColumnID')
        expect(SomeReadModel.someObserverArray).to.have.been.calledWithMatch(anEntityInstance, 'joinColumnID', {
          id: 'joinColumnID',
          kind: 'some',
          count: 77,
          boosterMetadata: {
            version: someReadModelStoredVersion,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntityWithArray',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserverArray',
            },
          },
        })
        expect(SomeReadModel.someObserverArray).to.have.returned({
          id: 'joinColumnID',
          kind: 'some',
          count: 200,
          boosterMetadata: {
            version: someReadModelStoredVersion + 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntityWithArray',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserverArray',
            },
          },
        })
        expect(SomeReadModel.someObserverArray).to.have.been.calledWithMatch(
          anEntityInstance,
          'anotherJoinColumnID',
          null
        )
        expect(SomeReadModel.someObserverArray).to.have.returned({
          id: 'anotherJoinColumnID',
          kind: 'some',
          count: 123,
          boosterMetadata: {
            version: 1,
            schemaVersion: 1,
            lastUpdateAt: '1970-01-01T00:00:00.000Z',
            lastProjectionInfo: {
              entityId: 'importantEntityID',
              entityName: 'AnImportantEntityWithArray',
              entityUpdatedAt: '1970-01-01T00:00:00.000Z',
              projectionMethod: 'SomeReadModel.someObserverArray',
            },
          },
        })

        expect(config.provider.readModels.store).to.have.been.calledTwice
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          SomeReadModel.name,
          {
            id: 'joinColumnID',
            kind: 'some',
            count: 200,
            boosterMetadata: {
              version: someReadModelStoredVersion + 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntityWithArray',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'SomeReadModel.someObserverArray',
              },
            },
          },
          someReadModelStoredVersion
        )
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          SomeReadModel.name,
          {
            id: 'anotherJoinColumnID',
            kind: 'some',
            count: 123,
            boosterMetadata: {
              version: 1,
              schemaVersion: 1,
              lastUpdateAt: '1970-01-01T00:00:00.000Z',
              lastProjectionInfo: {
                entityId: 'importantEntityID',
                entityName: 'AnImportantEntityWithArray',
                entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                projectionMethod: 'SomeReadModel.someObserverArray',
              },
            },
          },
          0
        )
      })
    })

    context('when there is high contention and optimistic concurrency is needed for Array joinKey projections', () => {
      it('The retries are independent for all Read Models in the array, retries 5 times when the error OptimisticConcurrencyUnexpectedVersionError happens 4 times', async () => {
        let tryNumber = 1
        const expectedAnotherJoinColumnIDTries = 5
        const expectedJoinColumnIDTries = 1
        const fakeStore = fake(
          (config: BoosterConfig, readModelName: string, readModel: ReadModelInterface): Promise<unknown> => {
            if (readModelName === SomeReadModel.name) {
              if (readModel.id == 'anotherJoinColumnID' && tryNumber < expectedAnotherJoinColumnIDTries) {
                tryNumber++
                throw new OptimisticConcurrencyUnexpectedVersionError('test error')
              }
            }
            return Promise.resolve()
          }
        )
        replace(config.provider.readModels, 'store', fakeStore)

        const readModelStore = new ReadModelStore(config)
        await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntityWithArray.name))

        const someReadModelStoreCalls = fakeStore.getCalls().filter((call) => call.args[1] === SomeReadModel.name)
        expect(someReadModelStoreCalls).to.be.have.length(expectedJoinColumnIDTries + expectedAnotherJoinColumnIDTries)
        someReadModelStoreCalls
          .filter((call) => call.args[3].id == 'joinColumnID')
          .forEach((call) => {
            expect(call.args).to.be.deep.equal([
              config,
              SomeReadModel.name,
              {
                id: 'joinColumnID',
                kind: 'some',
                count: 123,
                boosterMetadata: { version: 1 },
              },
              0,
            ])
          })
        someReadModelStoreCalls
          .filter((call) => call.args[3].id == 'anotherJoinColumnID')
          .forEach((call) => {
            expect(call.args).to.be.deep.equal([
              config,
              SomeReadModel.name,
              {
                id: 'anotherJoinColumnID',
                kind: 'some',
                count: 123,
                boosterMetadata: { version: 1 },
              },
              0,
            ])
          })
      })
    })

    context('for read models with defined sequenceKeys', () => {
      beforeEach(() => {
        config.readModelSequenceKeys['AnotherReadModel'] = 'count'
      })

      afterEach(() => {
        delete config.readModelSequenceKeys.AnotherReadModel
      })

      it('applies the projections with the right sequenceMetadata', async () => {
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const anEntityInstance = createInstance(AnImportantEntity, anEntitySnapshot.value) as any
        const readModelStore = new ReadModelStore(config)
        const fakeApplyProjectionToReadModel = fake()
        replace(readModelStore as any, 'applyProjectionToReadModel', fakeApplyProjectionToReadModel)

        await readModelStore.project(anEntitySnapshot)

        expect(fakeApplyProjectionToReadModel).to.have.been.calledThrice
        for (const projectionMetadata of config.projections[AnImportantEntity.name]) {
          const readModelClassName = projectionMetadata.class.name
          expect(fakeApplyProjectionToReadModel).to.have.been.calledWith(
            anEntityInstance,
            projectionMetadata,
            readModelClassName,
            anEntityInstance[projectionMetadata.joinKey],
            readModelClassName === 'AnotherReadModel' ? { name: 'count', value: 123 } : undefined
          )
        }
      })
    })
  })

  describe('the `fetchReadModel` method', () => {
    context('with no sequenceMetadata', () => {
      it("returns `undefined` when the read model doesn't exist", async () => {
        replace(config.provider.readModels, 'fetch', fake.returns(undefined))
        const readModelStore = new ReadModelStore(config)

        const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID')

        expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
          config,
          SomeReadModel.name,
          'joinColumnID',
          undefined
        )
        expect(result).to.be.undefined
      })

      it("returns `undefined` when the read model doesn't exist and provider returns [undefined]", async () => {
        replace(config.provider.readModels, 'fetch', fake.returns([undefined]))
        const readModelStore = new ReadModelStore(config)

        const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID')

        expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
          config,
          SomeReadModel.name,
          'joinColumnID',
          undefined
        )
        expect(result).to.be.undefined
      })

      it('returns an instance of the current read model value when it exists', async () => {
        replace(config.provider.readModels, 'fetch', fake.returns([{ id: 'joinColumnID' }]))
        const readModelStore = new ReadModelStore(config)

        const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID')

        expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
          config,
          SomeReadModel.name,
          'joinColumnID',
          undefined
        )
        expect(result).to.be.deep.equal(new SomeReadModel('joinColumnID'))
      })
    })

    context('with sequenceMetadata', () => {
      it("calls the provider's fetch method passing the sequenceMetadata object", async () => {
        replace(config.provider.readModels, 'fetch', fake.returns({ id: 'joinColumnID' }))
        const readModelStore = new ReadModelStore(config)

        await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID', {
          name: 'time',
          value: 'now!',
        })

        expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
          config,
          SomeReadModel.name,
          'joinColumnID',
          { name: 'time', value: 'now!' }
        )
      })
    })
  })

  describe('the `joinKeyForProjection` private method', () => {
    context('when the joinKey exists', () => {
      it('returns the joinKey value', () => {
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const anEntityInstance = createInstance(AnImportantEntity, anEntitySnapshot.value) as any
        const readModelStore = new ReadModelStore(config) as any

        expect(readModelStore.joinKeyForProjection(anEntityInstance, { joinKey: 'someKey' })).to.be.deep.equal([
          'joinColumnID',
        ])
      })
    })

    context('when the joinkey does not exist', () => {
      it('should not throw and error an skip', () => {
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const anEntityInstance = createInstance(AnImportantEntity, anEntitySnapshot.value) as any
        const readModelStore = new ReadModelStore(config) as any
        expect(readModelStore.joinKeyForProjection(anEntityInstance, { joinKey: 'whatever' })).to.be.undefined
      })
    })
  })

  describe('the `sequenceKeyForProjection` private method', () => {
    context('when there is no sequence key for the read model in the config', () => {
      it('returns undefined', () => {
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const anEntityInstance = createInstance(AnImportantEntity, anEntitySnapshot.value) as any
        const readModelStore = new ReadModelStore(config) as any

        expect(readModelStore.sequenceKeyForProjection(anEntityInstance, { class: SomeReadModel })).to.be.undefined
      })
    })

    context('when there is a sequence key for the read model in the config', () => {
      beforeEach(() => {
        config.readModelSequenceKeys['AnotherReadModel'] = 'count'
      })

      afterEach(() => {
        delete config.readModelSequenceKeys.AnotherReadModel
      })

      it('returns a `SequenceMetadata`object with the right sequenceKeyName and sequenceValue values', () => {
        const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name)
        const anEntityInstance = createInstance(AnImportantEntity, anEntitySnapshot.value) as any
        const readModelStore = new ReadModelStore(config) as any

        expect(readModelStore.sequenceKeyForProjection(anEntityInstance, { class: AnotherReadModel })).to.be.deep.equal(
          {
            name: 'count',
            value: 123,
          }
        )
      })
    })
  })

  // TODO: This method is tested indirectly in the `project` method tests, but it would be nice to have dedicated unit tests for it too
  describe('the `applyProjectionToReadModel` private method', () => {
    context('when `ReadModelAction.Delete` is returned', () => {
      it('deletes the read model') // TODO
    })
    context('when `ReadModelAction.Nothing` is returned', () => {
      it('does not update the read model state') // TODO
    })
    context('with no sequenceMetadata', () => {
      it('calls the `fetchReadodel` method with no sequenceMetadata object') // TODO
    })
    context('with sequenceMetadata', () => {
      it('calls the `fetchReadModel` method passing the sequenceMetadata object') // TODO
    })
  })
})
