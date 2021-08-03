/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from '../expect'
import { describe } from 'mocha'
import { ReadModel, Booster, Entity, Projects, sortBy } from '../../src'
import { UUID, ProjectionResult } from '@boostercloud/framework-types'

describe('the `ReadModel` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      for (const propName in config.readModels) {
        delete config.readModels[propName]
      }
    })
  })

  it('registers the read model in Booster configuration', () => {
    @ReadModel({
      authorize: 'all',
    })
    class SomeReadModel {
      public constructor(readonly id: UUID, readonly aStringProp: string, readonly aNumberProp: number) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModels['SomeReadModel']).to.be.deep.equal({
      class: SomeReadModel,
      authorizedRoles: 'all',
      before: [],
      properties: [
        {
          name: 'id',
          typeInfo: {
            name: 'UUID',
            parameters: [],
            type: UUID,
          },
        },
        {
          name: 'aStringProp',
          typeInfo: {
            name: 'String',
            parameters: [],
            type: String,
          },
        },
        {
          name: 'aNumberProp',
          typeInfo: {
            name: 'Number',
            parameters: [],
            type: Number,
          },
        },
      ],
    })
  })
})

describe('the `Projects` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      for (const propName in config.readModels) {
        delete config.readModels[propName]
      }
      for (const propName in config.projections) {
        delete config.projections[propName]
      }
    })
  })

  it('registers a read model method as an entity projection in Booster configuration', () => {
    @Entity
    class SomeEntity {
      public constructor(readonly id: UUID) {}
    }

    @ReadModel({
      authorize: 'all',
    })
    class SomeReadModel {
      public constructor(readonly id: UUID) {}

      @Projects(SomeEntity, 'id')
      public static observeSomeEntity(entity: SomeEntity): ProjectionResult<SomeReadModel> {
        throw new Error(`not implemented for ${entity}`)
      }
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any
    const someEntityObservers = booster.config.projections['SomeEntity']

    expect(booster.config.readModels).to.contain(SomeReadModel)
    expect(someEntityObservers).to.be.an('Array')
    expect(someEntityObservers).to.deep.include({
      class: SomeReadModel,
      methodName: 'observeSomeEntity',
      joinKey: 'id',
    })
  })

  describe('the `sortBy` decorator', () => {
    afterEach(() => {
      Booster.configure('test', (config) => {
        for (const propName in config.readModels) {
          delete config.readModels[propName]
        }
        for (const propName in config.projections) {
          delete config.projections[propName]
        }
      })
    })

    it('registers a sort key in the read model', () => {
      @ReadModel({
        authorize: 'all',
      })
      class SortedReadModel {
        public constructor(readonly id: UUID, @sortBy readonly timestamp: string) {}
      }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const booster = Booster as any

      expect(booster.config.readModelSortKeys).not.to.be.null
      expect(booster.config.readModelSortKeys[SortedReadModel.name]).to.be.a('String')
      expect(booster.config.readModelSortKeys[SortedReadModel.name]).to.be.equal('timestamp')
    })
  })
})
