import { FilterFor, ProjectionMetadata, ProviderLibrary, ReadModelInterface } from '@boostercloud/framework-types'
import { AnEntity, SomeReadModel } from './projection-store-classes-test'
import { Booster } from '../../../src'
import { expect } from '../../expect'
import { ProjectFunction } from '../../../src/services/read-model-projections/project-function'

function setupConfiguration() {
  Booster.configureCurrentEnv((config) => {
    config.provider = {
      readModels: {
        store: () => {},
        delete: () => {},
        fetch: () => {},
      },
    } as unknown as ProviderLibrary
  })
}

describe('ProjectionFunction', () => {
  before(() => {
    setupConfiguration()
  })

  describe('when join key is by entity', () => {
    it('readmodel and id are undefined, should call the projection function with read model undefined', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserver',
        joinKey: 'someKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, undefined, undefined)
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal(anEntity.someKey)
      expect(result.count).to.be.equal(0)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })

    it('id is undefined, should call the projection function with read model', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserver',
        joinKey: 'someKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const someReadModel = new SomeReadModel('someArrayKeyId', 'some', 'someArrayKey', 10)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, someReadModel, undefined)
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal(anEntity.someKey)
      expect(result.count).to.be.equal(11)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })
  })

  describe('when join key is by Array entity', () => {
    it('readmodel and id are undefined, should call the projection function with read model undefined', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserverArray',
        joinKey: 'someArrayKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, undefined, undefined)
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal('NEW')
      expect(result.count).to.be.equal(0)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal(undefined)
    })

    it('readmodel and id are defined, should call the projection function with read model and read model id', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserverArray',
        joinKey: 'someArrayKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const someReadModel = new SomeReadModel('someArrayKey', 'some', 'someArrayKey', 10)
      const projectFunction = new ProjectFunction(
        Booster.config,
        entityProjection,
        anEntity,
        someReadModel,
        someReadModel.id
      )
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal(someReadModel.id)
      expect(result.count).to.be.equal(11)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('someArrayKey')
    })

    it('readmodel is undefined, should call the projection function without read model but a read model id', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserverArray',
        joinKey: 'someArrayKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, undefined, 'someArrayKey')
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal('someArrayKey')
      expect(result.count).to.be.equal(1)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('someArrayKey')
    })
  })

  describe('when join key is by read model query', () => {
    it('readmodel and id are undefined, should call the projection function with read model undefined', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'projectQueryReadModel',
        joinKey: (entity: AnEntity): FilterFor<SomeReadModel> | undefined => {
          return {
            id: {
              eq: entity.id,
            },
          }
        },
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, undefined, undefined)
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal('NEW_QUERY')
      expect(result.count).to.be.equal(0)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })

    it('readmodel and id are defined, should call the projection function with read model and read model id', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'projectQueryReadModel',
        joinKey: (entity: AnEntity): FilterFor<SomeReadModel> | undefined => {
          return {
            id: {
              eq: entity.id,
            },
          }
        },
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const someReadModel = new SomeReadModel('someArrayKey', 'some', 'someArrayKey', 10)
      const projectFunction = new ProjectFunction(
        Booster.config,
        entityProjection,
        anEntity,
        someReadModel,
        someReadModel.id
      )
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal(someReadModel.id)
      expect(result.count).to.be.equal(11)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })

    it('id is undefined, should call the projection function with read model', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'projectQueryReadModel',
        joinKey: (entity: AnEntity): FilterFor<SomeReadModel> | undefined => {
          return {
            id: {
              eq: entity.id,
            },
          }
        },
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const someReadModel = new SomeReadModel('someArrayKeyId', 'some', 'someArrayKey', 10)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, someReadModel, undefined)
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal('NEW_QUERY')
      expect(result.count).to.be.equal(11)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })

    it('readmodel is undefined, should call the projection function without read model but a read model id', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'projectQueryReadModel',
        joinKey: (entity: AnEntity): FilterFor<SomeReadModel> | undefined => {
          return {
            id: {
              eq: entity.id,
            },
          }
        },
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 1)
      const projectFunction = new ProjectFunction(Booster.config, entityProjection, anEntity, undefined, 'someArrayKey')
      const result = (await projectFunction.callFunction()) as ReadModelInterface
      expect(result).to.be.instanceOf(SomeReadModel)
      expect(result.id).to.be.equal('someArrayKey')
      expect(result.count).to.be.equal(1)
      expect(result.kind).to.be.equal('some')
      expect(result.kindArray).to.be.equal('')
    })
  })
})
