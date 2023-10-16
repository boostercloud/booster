import { FilterFor, ProjectionMetadata, ProviderLibrary } from '@boostercloud/framework-types'
import { BoosterAuthorizer } from '../../../src/booster-authorizer'
import { AnEntity, SomeReadModel } from './projection-store-classes-test'
import { expect } from '../../expect'
import { Booster } from '../../../src'
import { ProjectFilterFor } from '../../../src/services/read-model-projections/project-filter-for'

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

describe('ProjectFilterFor', () => {
  before(() => {
    setupConfiguration()
  })

  describe('when join key is by entity', () => {
    it('return expected filter for with values replaced', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserver',
        joinKey: 'someKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const entityMetadata = {
        class: AnEntity,
        eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
      }
      const projectFunction = new ProjectFilterFor(Booster.config, anEntity, entityProjection, entityMetadata)
      const result = await projectFunction.filterForProjection()
      expect(result).to.be.eql({ id: { in: ['somekey'] } })
    })
  })

  describe('when join key is by Array entity', () => {
    it('return expected filter for with values replaced', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'someObserverArray',
        joinKey: 'someArrayKey',
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const entityMetadata = {
        class: AnEntity,
        eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
      }
      const projectFunction = new ProjectFilterFor(Booster.config, anEntity, entityProjection, entityMetadata)
      const result = await projectFunction.filterForProjection()
      expect(result).to.be.eql({ id: { in: ['someArrayKey'] } })
    })
  })

  describe('when join key is by read model query', () => {
    it('return expected filter for with values replaced', async () => {
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
      const entityMetadata = {
        class: AnEntity,
        eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
      }
      const projectFunction = new ProjectFilterFor(Booster.config, anEntity, entityProjection, entityMetadata)
      const result = await projectFunction.filterForProjection()
      expect(result).to.be.eql({ id: { eq: 'id' } })
    })

    it('return undefined if joinkey returns undefined', async () => {
      const entityProjection = {
        class: SomeReadModel,
        methodName: 'projectQueryReadModel',
        joinKey: (entity: AnEntity): FilterFor<SomeReadModel> | undefined => {
          if (entity.id === 'id') return undefined
          return {
            id: {
              eq: entity.id,
            },
          }
        },
      } as ProjectionMetadata<any, any>
      const anEntity = new AnEntity('id', 'somekey', ['someArrayKey'], 0)
      const entityMetadata = {
        class: AnEntity,
        eventStreamAuthorizer: BoosterAuthorizer.authorizeRoles.bind(null, []),
      }
      const projectFunction = new ProjectFilterFor(Booster.config, anEntity, entityProjection, entityMetadata)
      const result = await projectFunction.filterForProjection()
      expect(result).to.be.undefined
    })
  })
})
