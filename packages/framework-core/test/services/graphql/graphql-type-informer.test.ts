import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import { AnyClass, Logger } from '@boostercloud/framework-types'
import { expect } from '../../expect'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLFloat,
  GraphQLID,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLString,
} from 'graphql'
import { TypeMetadata } from 'metadata-booster'
import { GraphQLJSONObject } from 'graphql-type-json'
import { random } from 'faker'
import { GraphQLEnumValueConfig, GraphQLEnumValueConfigMap } from 'graphql/type/definition'
import { DateScalar } from '../../../src/services/graphql/common'

describe('GraphQLTypeInformer', () => {
  let sut: GraphQLTypeInformer
  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
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
        GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLJSONObject)))))
      )
      expect(otherParameterValue).to.be.deep.equal(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLJSONObject))))
    })

    describe('Get or create GraphQLType', () => {
      beforeEach(() => {
        sut = new GraphQLTypeInformer(logger)
      })

      it('should return GraphQLID!', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'UUID', // UUID and Date types are by name
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLID))
      })

      it('should return Date!', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'Date', // UUID and Date types are by name
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(DateScalar))
      })

      it('should return GraphQLString', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'string',
          typeGroup: 'String', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLString))
      })

      it('should return GraphQLFloat', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'number',
          typeGroup: 'Number', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLFloat))
      })

      it('should return GraphQLBoolean', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'boolean',
          typeGroup: 'Boolean', // by typeGroup
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLBoolean))
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

        const expectedResult = GraphQLNonNull(
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

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLBoolean))))
      })

      it('should return GraphQLJSONObject', () => {
        const result = sut.getOrCreateGraphQLType({
          name: 'MyObject',
          typeGroup: 'Object',
        } as TypeMetadata)

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLJSONObject))
      })

      describe('default', () => {
        let mockType: string

        beforeEach(() => {
          mockType = random.arrayElement(['Float32Array', 'Float32Array', 'Uint8Array', 'Promise'])
        })

        it('should return GraphQLJSONObject', () => {
          const result = sut.getOrCreateGraphQLType({
            name: `MyObject${mockType}`,
            typeGroup: mockType,
            parameters: [],
            isNullable: false,
          } as TypeMetadata)

          expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLJSONObject))
        })
      })
    })
  })
})
