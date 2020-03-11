/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from 'chai'
import { describe } from 'mocha'
import { ReadModel, Entity, Projects } from '../../src/index'
import { Booster } from '../../src/booster'
import { UUID } from '@boostercloud/framework-types'

describe('the `ReadModel` decorator', () => {
  beforeEach(() => {
    Booster.environment('test', (config) => {
      for (const propName in config.readModels) {
        delete config.readModels[propName]
      }
    })
    process.env.BOOSTER_ENV = 'test'
    Booster.selectEnvironment()
  })

  it('registers the read model in Booster configuration', () => {
    @ReadModel({
      authorize: 'all',
    })
    class SomeReadModel {
      public constructor(readonly id: UUID) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModels['SomeReadModel']).to.be.deep.equal({
      class: SomeReadModel,
      authorizedRoles: 'all',
    })
  })
})

describe('the `Projection` decorator', () => {
  beforeEach(() => {
    Booster.environment('test', (config) => {
      for (const propName in config.readModels) {
        delete config.readModels[propName]
      }
      for (const propName in config.projections) {
        delete config.projections[propName]
      }
    })
    process.env.BOOSTER_ENV = 'test'
    Booster.selectEnvironment()
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
      public static observeSomeEntity(entity: SomeEntity): SomeReadModel {
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
})
