import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import { AnyClass, Logger } from '@boostercloud/framework-types'
import { expect } from '../../expect'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLEnumValueConfig,
  GraphQLEnumValueConfigMap,
  GraphQLFloat,
  GraphQLID,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLString,
} from 'graphql'
import { TypeMetadata } from '@boostercloud/metadata-booster'
import { GraphQLJSON } from 'graphql-scalars'
import { random } from 'faker'
import { DateScalar } from '../../../src/services/graphql/common'

describe('GraphQLTypeInformer', () => {
  let sut: GraphQLTypeInformer
  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
    warn() {},
  }

  beforeEach(() => {
    sut = new GraphQLTypeInformer(logger)
  })

  describe('generateGraphQLTypeForClass', () => {
    interface Parameters {
      value: string
    }

    class TestClass {
      public someProperty: string
      public someParameters: ReadonlyArray<Array<Parameters>>
      public otherParameter: readonly Parameters[]

      constructor(
        someProperty: string,
        someParameters: ReadonlyArray<Array<Parameters>>,
        otherParameter: readonly Parameters[]
      ) {
        this.someProperty = someProperty
        this.someParameters = someParameters
        this.otherParameter = otherParameter
      }
    }

    it('should return expected GraphQL Type', () => {
      const result = sut.generateGraphQLTypeForClass(TestClass as AnyClass)
      expect(result.toString()).to.be.deep.equal('TestClass')
    })

    it('should process complex ReadonlyArray', () => {
      const result = sut.generateGraphQLTypeForClass(TestClass as AnyClass)
      const someParametersValue =
        result instanceof GraphQLObjectType ? result.getFields()['someParameters'].type : undefined
      const otherParameterValue =
        result instanceof GraphQLObjectType ? result.getFields()['otherParameter'].type : undefined
      expect(someParametersValue).to.be.deep.equal(
        new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(GraphQLJSON)))))
      )
      expect(otherParameterValue).to.be.deep.equal(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(GraphQLJSON))))
    })

    describe('Get or create GraphQLType', () => {
      beforeEach(() => {
        sut = new GraphQLTypeInformer(logger)
      })

      it('should return GraphQLID!', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'UUID', // UUID and Date types are by name
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLID))
      })

      it('should return Date!', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'Date', // UUID and Date types are by name
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(DateScalar))
      })

      it('should return GraphQLString', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'string',
          typeGroup: 'String', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLString))
      })

      it('should return GraphQLFloat', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'number',
          typeGroup: 'Number', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLFloat))
      })

      it('should return GraphQLBoolean', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'boolean',
          typeGroup: 'Boolean', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLBoolean))
      })

      it('should return GraphQLEnumType', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'enum',
          typeGroup: 'Enum', // by typeGroup
          parameters: [
            {
              name: 'key',
              typeGroup: 'Boolean',
              parameters: [],
              isNullable: false,
            },
          ],
          isNullable: false,
        } as TypeMetadata)

        const expectedResult = new GraphQLNonNull(
          new GraphQLEnumType({
            name: 'enum',
            values: {
              key: {
                value: 'key',
              } as GraphQLEnumValueConfig,
            } as GraphQLEnumValueConfigMap,
          })
        )

        expect(result).to.be.deep.equal(expectedResult)
      })

      it('should return GraphQLList of GraphQLBoolean', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'MyArray[]',
          typeGroup: 'Array', // by typeGroup
          parameters: [
            {
              name: 'boolean',
              typeGroup: 'Boolean',
              parameters: [],
              isNullable: false,
            },
          ],
          isNullable: false,
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(GraphQLBoolean))))
      })

      it('should return GraphQLJSON', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'MyObject',
          typeGroup: 'Object',
        } as TypeMetadata)

        expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLJSON))
      })

      describe('default', () => {
        let mockType: string

        beforeEach(() => {
          mockType = random.arrayElement(['Float32Array', 'Float32Array', 'Uint8Array', 'Promise'])
        })

        it('should return GraphQLJSON', () => {
          const result = sut.getOrCreateGraphQLType({
            name: `MyObject${mockType}`,
            typeGroup: mockType,
            parameters: [],
            isNullable: false,
          } as TypeMetadata)

          expect(result).to.be.deep.equal(new GraphQLNonNull(GraphQLJSON))
        })
      })
    })
  })
})
