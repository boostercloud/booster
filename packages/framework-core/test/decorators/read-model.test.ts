/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from '../expect'
import { describe } from 'mocha'
import { ReadModel, Booster, Entity, Projects, sequencedBy } from '../../src'
import { UUID, ProjectionResult } from '@boostercloud/framework-types'
import { BoosterAuthorizer } from '../../src/booster-authorizer'

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
      public constructor(
        readonly id: UUID,
        readonly aStringProp: string,
        readonly aNumberProp: number,
        readonly aReadonlyArray: ReadonlyArray<string>
      ) {}
    }

    // Make Booster be of any type to access private members
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const booster = Booster as any

    expect(booster.config.readModels['SomeReadModel']).to.be.deep.equal({
      class: SomeReadModel,
      authorizer: BoosterAuthorizer.allowAccess,
      before: [],
      properties: [
        {
          name: 'id',
          typeInfo: {
            importPath: '@boostercloud/framework-types',
            isNullable: false,
            name: 'UUID',
            parameters: [],
            type: UUID,
            typeGroup: 'Class',
            typeName: 'UUID',
          },
        },
        {
          name: 'aStringProp',
          typeInfo: {
            isNullable: false,
            name: 'string',
            parameters: [],
            type: String,
            typeGroup: 'String',
            typeName: 'String',
          },
        },
        {
          name: 'aNumberProp',
          typeInfo: {
            isNullable: false,
            name: 'number',
            parameters: [],
            type: Number,
            typeGroup: 'Number',
            typeName: 'Number',
          },
        },
        {
          name: 'aReadonlyArray',
          typeInfo: {
            isNullable: false,
            name: 'readonly string[]',
            parameters: [
              {
                isNullable: false,
                name: 'string',
                parameters: [],
                type: String,
                typeGroup: 'String',
                typeName: 'String',
              },
            ],
            type: undefined,
            typeGroup: 'ReadonlyArray',
            typeName: 'ReadonlyArray',
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

  describe('the `sequencedBy` decorator', () => {
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

    it('registers a sequence key in the read model', () => {
      @ReadModel({
        authorize: 'all',
      })
      class SequencedReadModel {
        public constructor(readonly id: UUID, @sequencedBy readonly timestamp: string) {}
      }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const booster = Booster as any

      expect(booster.config.readModelSequenceKeys).not.to.be.null
      expect(booster.config.readModelSequenceKeys[SequencedReadModel.name]).to.be.a('String')
      expect(booster.config.readModelSequenceKeys[SequencedReadModel.name]).to.be.equal('timestamp')
    })
  })
})
