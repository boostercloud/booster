import {
  BoosterConfig,
  EntitySnapshotEnvelope,
  ProjectionMetadata,
  ProviderLibrary,
} from '@boostercloud/framework-types'
import { fake, match, replace, restore, SinonStub, stub } from 'sinon'
import { BoosterAuthorizer } from '../../../src/booster-authorizer'
import { AnEntity, SomeReadModel } from './projection-store-classes-test'
import { expect } from '../../expect'
import { Booster } from '../../../src'
import { ReadModelSchemaMigrator } from '../../../src/read-model-schema-migrator'
import { ProjectionStore } from '../../../src/services/read-model-projections/projection-store'

describe('ProjectionStore', () => {
  let migrateStub: SinonStub

  afterEach(() => {
    migrateStub.restore()
    restore()
  })

  setupConfiguration()

  describe('when the projection returns a ReadModel', () => {
    it('and there was not previous read model, migrate is not call and a new read model is stored', async () => {
      const anEntity = new AnEntity('id', 'someKey', [], 1)

      // Migrate stub
      migrateStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      migrateStub.callsFake(async (_readModel, _readModelName) => {})
      // Store stub
      const someReadModel = new SomeReadModel('id', 'some', '', 10)
      replace(Booster.config.provider.readModels, 'store', fake.returns(someReadModel))

      const config = Booster.config as BoosterConfig
      const projectionStore = new ProjectionStore(config, anEntity, config.projections[AnEntity.name][0])
      const result = await projectionStore.projectNewReadModelAndStore()

      expect(result).to.be.deep.equal(someReadModel)
      expect(migrateStub).not.to.have.been.called
      expect(config.provider.readModels.store).to.have.been.calledOnceWithExactly(
        Booster.config,
        SomeReadModel.name,
        match({
          id: 'someKey',
          kind: 'some',
          count: 1,
          boosterMetadata: {
            version: 1,
            schemaVersion: 1,
            lastProjectionInfo: {
              entityId: 'id',
              entityName: undefined,
              entityUpdatedAt: undefined,
              projectionMethod: 'SomeReadModel.someObserver',
            },
          },
        }),
        0
      )
    })

    it('and there is a read model, migrate is call and the updated read model is stored', async () => {
      const anEntity = new AnEntity('id', 'someKey', [], 1)

      // Migrate stub
      migrateStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      migrateStub.callsFake(async (readModel, _readModelName) => {
        return {
          ...readModel,
          boosterMetadata: {
            version: 1,
          },
        }
      })
      // Store stub
      const someReadModel = new SomeReadModel('id', 'some', '', 10)
      replace(Booster.config.provider.readModels, 'store', fake.returns(someReadModel))

      const config = Booster.config as BoosterConfig
      const entitySnapshot: EntitySnapshotEnvelope = {
        version: 1,
        kind: 'snapshot',
        superKind: 'domain',
        entityID: anEntity.id,
        entityTypeName: AnEntity.name,
        value: anEntity as any,
        requestID: 'whatever',
        typeName: AnEntity.name,
        createdAt: new Date().toISOString(),
        persistedAt: new Date().toISOString(),
        snapshottedEventCreatedAt: new Date().toISOString(),
      }
      const projectionStore = new ProjectionStore(
        config,
        anEntity,
        config.projections[AnEntity.name][0],
        someReadModel,
        entitySnapshot
      )
      const result = await projectionStore.projectNewReadModelAndStore()

      expect(result).to.be.deep.equal(someReadModel)
      expect(migrateStub).to.have.been.calledOnceWithExactly(someReadModel, SomeReadModel.name)
      expect(config.provider.readModels.store).to.have.been.calledOnceWithExactly(
        Booster.config,
        SomeReadModel.name,
        match({
          id: 'someKey',
          kind: 'some',
          count: 11,
          boosterMetadata: {
            version: 2,
            schemaVersion: 1,
            lastProjectionInfo: {
              entityId: 'id',
              entityName: AnEntity.name,
              projectionMethod: 'SomeReadModel.someObserver',
            },
          },
        }),
        1
      )
    })
  })

  describe('when the projection returns Nothing', () => {
    it('returns nothing', async () => {
      const anEntity = new AnEntity('id', 'some', [], 1)

      // Migrate stub
      migrateStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      migrateStub.callsFake(async (readModel, _readModelName) => {
        return {
          ...readModel,
          boosterMetadata: {
            version: 1,
          },
        }
      })
      // Store stub
      const someReadModel = new SomeReadModel('id', 'some', '', 10)
      replace(Booster.config.provider.readModels, 'store', fake.returns(someReadModel))
      // Delete stub
      replace(Booster.config.provider.readModels, 'delete', fake())

      const config = Booster.config as BoosterConfig
      const entitySnapshot: EntitySnapshotEnvelope = {
        version: 1,
        kind: 'snapshot',
        superKind: 'domain',
        entityID: anEntity.id,
        entityTypeName: AnEntity.name,
        value: anEntity as any,
        requestID: 'whatever',
        typeName: AnEntity.name,
        createdAt: new Date().toISOString(),
        persistedAt: new Date().toISOString(),
        snapshottedEventCreatedAt: new Date().toISOString(),
      }
      const projectionElement = config.projections[AnEntity.name].find(
        (projection) => projection.methodName === 'projectionThatReturnsNothing'
      )!

      const projectionStore = new ProjectionStore(config, anEntity, projectionElement, someReadModel, entitySnapshot)
      const result = await projectionStore.projectNewReadModelAndStore()

      expect(migrateStub).to.have.been.calledOnceWithExactly(someReadModel, SomeReadModel.name)
      expect(config.provider.readModels.store).not.to.have.been.called
      expect(config.provider.readModels.delete).not.to.have.been.called
      expect(result).to.be.undefined
    })
  })

  describe('when the projection returns Delete', () => {
    it('delete is call', async () => {
      const anEntity = new AnEntity('id', 'some', [], 1)

      // Migrate stub
      migrateStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      migrateStub.callsFake(async (readModel, _readModelName) => {
        return {
          ...readModel,
          boosterMetadata: {
            version: 1,
          },
        }
      })
      // Store stub
      const someReadModel = new SomeReadModel('id', 'some', '', 10)
      replace(Booster.config.provider.readModels, 'store', fake.returns(someReadModel))
      // Delete stub
      replace(Booster.config.provider.readModels, 'delete', fake())

      const config = Booster.config as BoosterConfig
      const entitySnapshot: EntitySnapshotEnvelope = {
        version: 1,
        kind: 'snapshot',
        superKind: 'domain',
        entityID: anEntity.id,
        entityTypeName: AnEntity.name,
        value: anEntity as any,
        requestID: 'whatever',
        typeName: AnEntity.name,
        createdAt: new Date().toISOString(),
        persistedAt: new Date().toISOString(),
        snapshottedEventCreatedAt: new Date().toISOString(),
      }
      const projectionElement = config.projections[AnEntity.name].find(
        (projection) => projection.methodName === 'projectionThatReturnsDelete'
      )!

      const projectionStore = new ProjectionStore(config, anEntity, projectionElement, someReadModel, entitySnapshot)
      await projectionStore.projectNewReadModelAndStore()

      expect(migrateStub).to.have.been.calledOnceWithExactly(someReadModel, SomeReadModel.name)
      expect(config.provider.readModels.store).not.to.have.been.called
      expect(config.provider.readModels.delete).to.have.been.calledOnceWithExactly(
        config,
        SomeReadModel.name,
        match({
          ...someReadModel,
          boosterMetadata: {
            version: 1,
          },
        })
      )
    })
  })

  function setupConfiguration(): void {
    Booster.configureCurrentEnv((config) => {
      config.provider = {
        readModels: {
          store: () => {},
          delete: () => {},
          fetch: () => {},
        },
      } as unknown as ProviderLibrary

      config.entities[AnEntity.name] = {
        class: AnEntity,
        eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
      }
      config.readModels[SomeReadModel.name] = {
        class: SomeReadModel,
        authorizer: BoosterAuthorizer.allowAccess,
        properties: [],
        before: [],
      }
      config.projections[AnEntity.name] = [
        {
          class: SomeReadModel,
          methodName: 'someObserver',
          joinKey: 'someKey',
        } as ProjectionMetadata<any, any>,
        {
          class: SomeReadModel,
          methodName: 'projectionThatReturnsNothing',
          joinKey: 'someKey',
        } as ProjectionMetadata<any, any>,
        {
          class: SomeReadModel,
          methodName: 'projectionThatReturnsDelete',
          joinKey: 'someKey',
        } as ProjectionMetadata<any, any>,
      ]
    })
  }
})
