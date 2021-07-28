/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from '../expect'
import { describe } from 'mocha'
import { ReadModel, Booster } from '../../src'
import { UUID } from '@boostercloud/framework-types'

describe('the `References` constructor parameter decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      for (const propName in config.readModelReferences) {
        delete config.readModelReferences[propName]
      }
    })
  })

  it('registers a read model reference with an implicit foreign key in Booster configuration', () => {
    @ReadModel({
      authorize: 'all',
    })
    class ReferencedReadModel {
      public constructor(readonly id: UUID, readonly aStringProp: string) {}
    }

    @ReadModel({
        authorize: 'all',
    })
    class RefererReadModel {
        public constructor(
            readonly id: UUID, 
            @References readonly referencedReadModelId:UUID
        ) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModelReferences['RefererReadModel']).to.be.deep.equal([{
        referencedReadModel: ReferencedReadModel,
        foreignKey: 'referencedReadModelId',
    }]
  })

  it('registers a read model reference with an explicit foreign key in Booster configuration', () => {
    @ReadModel({
      authorize: 'all',
    })
    class ReferencedReadModel2 {
      public constructor(readonly id: UUID, readonly aStringProp: string) {}
    }

    @ReadModel({
        authorize: 'all',
    })
    class RefererReadModel2 {
        public constructor(
            readonly id: UUID, 
            @References(ReferencedReadModel2) readonly aRandomFKNameId:UUID
        ) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModelReferences['RefererReadModel2']).to.be.deep.equal([{
        referencedReadModel: ReferencedReadModel2,
        foreignKey: 'aRandomFKNameId',
    }]
  })

  it('Initializes the reference list for a read model in the Booster config when is accessed for the first time', () => {})
  it('does not insert duplicate references using the same class pair and foreign key', () => {})
  it('does not create references for implicit class names that do not exist', () => {})
  it('does not create references for classes that are not read models', () => {})
})


