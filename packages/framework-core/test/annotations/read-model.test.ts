/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from 'chai'
import { describe } from 'mocha'
import { ReadModel, Booster, Entity, Projects } from '../../src/index'
import { UUID } from '@boostercloud/framework-types'

describe('the `ReadModel` decorator', () => {
  it('registers the read model in Booster configuration', () => {
    @ReadModel
    class SomeReadModel {
      public constructor(readonly id: UUID) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModels).to.contain(SomeReadModel)
  })
})

describe('the `Projection` decorator', () => {
  it('registers a read model method as an entity projection in Booster configuration', () => {
    @Entity
    class SomeEntity {
      public constructor(readonly id: UUID) {}
    }

    @ReadModel
    class OtherReadModel {
      public constructor(readonly id: UUID) {}

      @Projects(SomeEntity, 'id')
      public static observeSomeEntity(entity: SomeEntity): OtherReadModel {
        throw new Error(`not implemented for ${entity}`)
      }
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any
    const someEntityObservers = booster.config.projections['SomeEntity']

    expect(booster.config.readModels).to.contain(OtherReadModel)
    expect(someEntityObservers).to.be.an('Array')
    expect(someEntityObservers).to.deep.include({
      class: OtherReadModel,
      methodName: 'observeSomeEntity',
      joinKey: 'id',
    })
  })
})
