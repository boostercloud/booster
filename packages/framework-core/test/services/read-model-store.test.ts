/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe } from 'mocha'
import { restore, fake, replace, spy } from 'sinon'
import { ReadModelStore } from '../../src/services/read-model-store'
import { buildLogger } from '../../src/booster-logger'
import {
  Level,
  Logger,
  BoosterConfig,
  EventEnvelope,
  UUID,
  ProviderLibrary,
  ReadModelAction,
  OptimisticConcurrencyUnexpectedVersionError,
} from '@boostercloud/framework-types'
import { expect } from '../expect'

describe('ReadModelStore', () => {
  afterEach(() => {
    restore()
  })

  const logger = buildLogger(Level.error)

  class ImportantConcept {
    public constructor(readonly id: UUID, readonly someKey: UUID, readonly count: number) {}
  }

  class SomeReadModel {
    public static someObserver(entity: ImportantConcept, obj: any): any {
      const count = (obj?.count || 0) + entity.count
      return { id: entity.someKey, kind: 'some', count: count }
    }
  }

  class AnotherReadModel {
    public static anotherObserver(entity: ImportantConcept, obj: any): any {
      const count = (obj?.count || 0) + entity.count
      return { id: entity.someKey, kind: 'another', count: count }
    }
  }

  const config = new BoosterConfig('test')
  config.provider = ({
    readModels: {
      store: () => {},
      delete: () => {},
      fetch: () => {},
    },
  } as unknown) as ProviderLibrary
  config.entities['ImportantConcept'] = { class: ImportantConcept, authorizeReadEvents: [] }
  config.projections['ImportantConcept'] = [
    {
      class: SomeReadModel,
      methodName: 'someObserver',
      joinKey: 'someKey',
    },
    {
      class: AnotherReadModel,
      methodName: 'anotherObserver',
      joinKey: 'someKey',
    },
  ]

  const anEntitySnapshot: EventEnvelope = {
    version: 1,
    kind: 'snapshot',
    entityID: '42',
    entityTypeName: 'ImportantConcept',
    value: new ImportantConcept('importantEntityID', 'joinColumnID', 123),
    requestID: 'whatever',

    typeName: 'ImportantConcept',
    createdAt: new Date().toISOString(),
  }

  describe('the `project` method', () => {
    context('when the entity class has no projections', () => {
      it('returns without errors and without performing any actions', async () => {
        const entitySnapshotWithNoProjections: EventEnvelope = {
          version: 1,
          kind: 'snapshot',
          entityID: '42',
          entityTypeName: 'AConceptWithoutProjections',
          value: { entityID: () => '42' },
          requestID: 'whatever',
          typeName: 'ImportantConcept',
          createdAt: new Date().toISOString(),
        }

        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config, logger)
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
        const readModelStore = new ReadModelStore(config, logger)

        await readModelStore.project(anEntitySnapshot)
        expect(config.provider.readModels.store).not.to.have.been.called
        expect(config.provider.readModels.delete).to.have.been.calledTwice
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
        const readModelStore = new ReadModelStore(config, logger)

        await readModelStore.project(anEntitySnapshot)
        expect(config.provider.readModels.store).not.to.have.been.called
        expect(config.provider.readModels.delete).not.to.have.been.called
      })
    })

    context("when the corresponding read models don't exist", () => {
      it('creates new instances of the read models', async () => {
        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config, logger)
        replace(readModelStore, 'fetchReadModel', fake.returns(null))
        spy(SomeReadModel, 'someObserver')
        spy(AnotherReadModel, 'anotherObserver')

        await readModelStore.project(anEntitySnapshot)

        expect(readModelStore.fetchReadModel).to.have.been.calledTwice
        expect(readModelStore.fetchReadModel).to.have.been.calledWith('SomeReadModel', 'joinColumnID')
        expect(readModelStore.fetchReadModel).to.have.been.calledWith('AnotherReadModel', 'joinColumnID')
        expect(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntitySnapshot.value, null)
        expect(SomeReadModel.someObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'some',
          count: 123,
          boosterMetadata: { version: 1 },
        })
        expect(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntitySnapshot.value, null)
        expect(AnotherReadModel.anotherObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'another',
          count: 123,
          boosterMetadata: { version: 1 },
        })
        expect(config.provider.readModels.store).to.have.been.calledTwice
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          logger,
          'SomeReadModel',
          {
            id: 'joinColumnID',
            kind: 'some',
            count: 123,
            boosterMetadata: { version: 1 },
          },
          0
        )
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          logger,
          'AnotherReadModel',
          {
            id: 'joinColumnID',
            kind: 'another',
            count: 123,
            boosterMetadata: { version: 1 },
          },
          0
        )
      })
    })

    context('when the corresponding read model did exist', () => {
      it('updates the read model', async () => {
        replace(config.provider.readModels, 'store', fake())
        const readModelStore = new ReadModelStore(config, logger)
        const someReadModelStoredVersion = 10
        const anotherReadModelStoredVersion = 32
        replace(
          readModelStore,
          'fetchReadModel',
          fake((className: string, id: UUID) => {
            if (className == 'SomeReadModel') {
              return { id: id, kind: 'some', count: 77, boosterMetadata: { version: someReadModelStoredVersion } }
            } else {
              return {
                id: id,
                kind: 'another',
                count: 177,
                boosterMetadata: { version: anotherReadModelStoredVersion },
              }
            }
          })
        )
        spy(SomeReadModel, 'someObserver')
        spy(AnotherReadModel, 'anotherObserver')

        await readModelStore.project(anEntitySnapshot)

        expect(readModelStore.fetchReadModel).to.have.been.calledTwice
        expect(readModelStore.fetchReadModel).to.have.been.calledWith('SomeReadModel', 'joinColumnID')
        expect(readModelStore.fetchReadModel).to.have.been.calledWith('AnotherReadModel', 'joinColumnID')
        expect(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntitySnapshot.value, {
          id: 'joinColumnID',
          kind: 'some',
          count: 77,
          boosterMetadata: { version: someReadModelStoredVersion },
        })
        expect(SomeReadModel.someObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'some',
          count: 200,
          boosterMetadata: { version: someReadModelStoredVersion + 1 },
        })
        expect(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntitySnapshot.value, {
          id: 'joinColumnID',
          kind: 'another',
          count: 177,
          boosterMetadata: { version: anotherReadModelStoredVersion },
        })
        expect(AnotherReadModel.anotherObserver).to.have.returned({
          id: 'joinColumnID',
          kind: 'another',
          count: 300,
          boosterMetadata: { version: anotherReadModelStoredVersion + 1 },
        })
        expect(config.provider.readModels.store).to.have.been.calledTwice
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          logger,
          'SomeReadModel',
          {
            id: 'joinColumnID',
            kind: 'some',
            count: 200,
            boosterMetadata: { version: someReadModelStoredVersion + 1 },
          },
          someReadModelStoredVersion
        )
        expect(config.provider.readModels.store).to.have.been.calledWith(
          config,
          logger,
          'AnotherReadModel',
          {
            id: 'joinColumnID',
            kind: 'another',
            count: 300,
            boosterMetadata: { version: anotherReadModelStoredVersion + 1 },
          },
          anotherReadModelStoredVersion
        )
      })
    })

    context('when there is high contention and optimistic concurrency is needed', () => {
      it('retries 5 times when the error OptimisticConcurrencyUnexpectedVersionError happens 4 times', async () => {
        let tryNumber = 1
        const expectedTries = 5
        const fakeStore = fake(
          (config: BoosterConfig, logger: Logger, readModelName: string): Promise<unknown> => {
            if (readModelName === SomeReadModel.name && tryNumber < expectedTries) {
              tryNumber++
              throw new OptimisticConcurrencyUnexpectedVersionError('test error')
            }
            return Promise.resolve()
          }
        )
        replace(config.provider.readModels, 'store', fakeStore)
        const readModelStore = new ReadModelStore(config, logger)
        await readModelStore.project(anEntitySnapshot)

        const someReadModelStoreCalls = fakeStore.getCalls().filter((call) => call.args[2] === SomeReadModel.name)
        expect(someReadModelStoreCalls).to.be.have.length(expectedTries)
        someReadModelStoreCalls.forEach((call) => {
          expect(call.args).to.be.deep.equal([
            config,
            logger,
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
      })
    })
  })

  describe('the `fetchReadModel` method', () => {
    it("returns null when the read model doesn't exist", async () => {
      replace(config.provider.readModels, 'fetch', fake.returns(null))
      const readModelStore = new ReadModelStore(config, logger)

      const result = await readModelStore.fetchReadModel('SomeReadModel', 'joinColumnID')

      expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
        config,
        logger,
        'SomeReadModel',
        'joinColumnID'
      )
      expect(result).to.be.null
    })

    it('returns the current read model value when it exists', async () => {
      replace(config.provider.readModels, 'fetch', fake.returns({ id: 'joinColumnID', count: 31415 }))
      const readModelStore = new ReadModelStore(config, logger)

      const result = await readModelStore.fetchReadModel('SomeReadModel', 'joinColumnID')

      expect(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(
        config,
        logger,
        'SomeReadModel',
        'joinColumnID'
      )
      expect(result).to.be.deep.equal({ id: 'joinColumnID', count: 31415 })
    })
  })

  describe('the `joinKeyForProjection` private method', () => {
    it('returns the value of the joinKey if it exists') // TODO
    it('raises an error when the joinkey do not exist') // TODO
  })

  describe('the `reducerForProjection` method', () => {
    it('returns the reducer function for a projection metadata') // TODO
    it('raises an error when the method is not found') // TODO
  })
})
