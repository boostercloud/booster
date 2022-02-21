/* eslint-disable @typescript-eslint/no-explicit-any */
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { SinonStub, stub, replace, SinonStubbedInstance, restore, fake } from 'sinon'
import { expect } from '../../expect'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import sinon = require('sinon')
import { GraphQLResolverContext } from '../../../src/services/graphql/common'
import {
  GraphQLBoolean,
  GraphQLFloat,
  GraphQLID,
  GraphQLInt,
  GraphQLString,
  GraphQLFieldConfigMap,
  GraphQLNonNull,
  GraphQLEnumType,
  GraphQLOutputType,
  GraphQLObjectType,
  GraphQLList,
} from 'graphql'
import { random } from 'faker'
import GraphQLJSON from 'graphql-type-json'
import { AnyClass, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ClassMetadata } from 'metadata-booster'
import * as metadata from '../../../src/decorators/metadata'

function buildFakeGraphQLFielConfigMap(name: string): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
  return {
    [name]: {
      type: GraphQLString,
      args: {},
      resolve: fake(),
    },
  }
}

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('GraphQLQueryGenerator', () => {
  afterEach(() => {
    restore()
  })

  describe('the `generate` public method', () => {
    const simpleConfig = new BoosterConfig('test')

    class SomeReadModel {}

    const typeInformer = new GraphQLTypeInformer(logger)

    const graphQLQueryGenerator = new GraphQLQueryGenerator(
      simpleConfig,
      [SomeReadModel],
      typeInformer,
      () => fake(),
      () => fake(),
      fake()
    )

    it('generates by Key queries', () => {
      const fakegenerateByKeysQueries = fake()
      replace(graphQLQueryGenerator as any, 'generateByKeysQueries', fakegenerateByKeysQueries)
      replace(graphQLQueryGenerator as any, 'generateFilterQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateListedQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateEventQueries', fake())

      graphQLQueryGenerator.generate()

      expect(fakegenerateByKeysQueries).to.have.been.calledOnce
    })

    it('generates filter queries', () => {
      replace(graphQLQueryGenerator as any, 'generateByKeysQueries', fake())
      const fakeGenerateFilterQueries = fake()
      replace(graphQLQueryGenerator as any, 'generateFilterQueries', fakeGenerateFilterQueries)
      replace(graphQLQueryGenerator as any, 'generateListedQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateEventQueries', fake())

      graphQLQueryGenerator.generate()

      expect(fakeGenerateFilterQueries).to.have.been.calledOnce
    })

    it('generates listed queries', () => {
      replace(graphQLQueryGenerator as any, 'generateByKeysQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateFilterQueries', fake())
      const fakeGenerateListedQueries = fake()
      replace(graphQLQueryGenerator as any, 'generateListedQueries', fakeGenerateListedQueries)
      replace(graphQLQueryGenerator as any, 'generateEventQueries', fake())

      graphQLQueryGenerator.generate()

      expect(fakeGenerateListedQueries).to.have.been.calledOnce
    })

    it('generates event queries', () => {
      replace(graphQLQueryGenerator as any, 'generateByKeysQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateFilterQueries', fake())
      replace(graphQLQueryGenerator as any, 'generateListedQueries', fake())
      const fakeGenerateEventQueries = fake()
      replace(graphQLQueryGenerator as any, 'generateEventQueries', fakeGenerateEventQueries)

      graphQLQueryGenerator.generate()

      expect(fakeGenerateEventQueries).to.have.been.calledOnce
    })

    it('returns a well-formed GraphQL Query Object Type', () => {
      const fakeIDQueries = buildFakeGraphQLFielConfigMap('IDQuery')
      replace(graphQLQueryGenerator as any, 'generateByKeysQueries', fake.returns(fakeIDQueries))
      const fakeFilterQueries = buildFakeGraphQLFielConfigMap('FilterQuery')
      replace(graphQLQueryGenerator as any, 'generateFilterQueries', fake.returns(fakeFilterQueries))
      const fakeListedQueries = buildFakeGraphQLFielConfigMap('ListedQuery')
      replace(graphQLQueryGenerator as any, 'generateListedQueries', fake.returns(fakeListedQueries))
      const fakeEventQueries = buildFakeGraphQLFielConfigMap('EventQuery')
      replace(graphQLQueryGenerator as any, 'generateEventQueries', fake.returns(fakeEventQueries))

      const result = graphQLQueryGenerator.generate()

      expect(result).to.have.property('name', 'Query')
      const fieldNames = Object.keys(result.getFields())
      expect(fieldNames).to.include('IDQuery')
      expect(fieldNames).to.include('FilterQuery')
      expect(fieldNames).to.include('ListedQuery')
      expect(fieldNames).to.include('EventQuery')
    })

    context('black box tests', () => {
      let mockGraphQLType: any

      let mockTypeInformer: SinonStubbedInstance<GraphQLTypeInformer>
      let mockByIdResolverBuilder: SinonStub
      let mockFilterResolverBuilder: SinonStub
      let mockEventsResolver: SinonStub

      let getGraphQLTypeForStub: SinonStub

      beforeEach(() => {
        mockGraphQLType = random.arrayElement([
          GraphQLBoolean,
          GraphQLID,
          GraphQLString,
          GraphQLFloat,
          GraphQLInt,
          GraphQLJSON,
        ])

        mockTypeInformer = sinon.createStubInstance(GraphQLTypeInformer)
        mockByIdResolverBuilder = stub()
        mockFilterResolverBuilder = stub()
        mockEventsResolver = stub()

        getGraphQLTypeForStub = stub().returns(mockGraphQLType)
        replace(mockTypeInformer, 'getOrCreateGraphQLType', getGraphQLTypeForStub as any)
        replace(mockTypeInformer, 'generateGraphQLTypeForClass', getGraphQLTypeForStub as any)
      })

      context('with target types', () => {
        context('1 target type', () => {
          let mockTargetTypeClass: BooleanConstructor | StringConstructor | NumberConstructor | ArrayConstructor
          let mockTargetTypeName: string
          let sut: GraphQLQueryGenerator
          let getClassMetadataStub: SinonStub<[classType: AnyClass], ClassMetadata>

          beforeEach(() => {
            mockTargetTypeClass = random.arrayElement([Boolean, String, Number, Array])
            mockTargetTypeName = mockTargetTypeClass.name

            const mockedClassMetadata = {
              name: mockTargetTypeClass.name,
              type: mockTargetTypeClass,
              fields: [],
              methods: [],
            } as ClassMetadata

            sut = new GraphQLQueryGenerator(
              simpleConfig,
              [mockTargetTypeClass],
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockFilterResolverBuilder,
              mockEventsResolver
            )

            getClassMetadataStub = sinon
              .stub(metadata, 'getClassMetadata')
              .withArgs(mockTargetTypeClass)
              .returns(mockedClassMetadata as ClassMetadata)
          })

          it('should call typeInformer.getGraphQLTypeFor thrice', () => {
            sut.generate()

            expect(getGraphQLTypeForStub).calledThrice.and.calledWith(mockTargetTypeClass)
          })

          it('should call filterResolverBuilder once with expected argument', () => {
            sut.generate()

            expect(mockByIdResolverBuilder).calledOnce.and.calledWith(mockTargetTypeClass)
            // @ts-ignore
            expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
          })

          it('should return expected result', () => {
            const result = sut.generate()

            expect(result.name).to.be.equal('Query')
            expect(result.description).to.be.undefined
            expect(result.extensions).to.be.undefined
            expect(result.astNode).to.be.undefined
            expect(result.extensionASTNodes).to.be.undefined

            const config: any = result.toConfig()
            expect(config.fields[mockTargetTypeName].description).to.be.undefined
            expect(config.fields[mockTargetTypeName].type).to.be.equal(mockGraphQLType)
            expect(config.fields[mockTargetTypeName].resolve).to.be.undefined
            expect(config.fields[mockTargetTypeName].subscribe).to.be.undefined
            expect(config.fields[mockTargetTypeName].deprecationReason).to.be.undefined
            expect(config.fields[mockTargetTypeName].extensions).to.be.undefined
            expect(config.fields[mockTargetTypeName].astNode).to.be.undefined

            expect(config.fields[`${mockTargetTypeName}s`].description).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].type.toString()).to.be.equal(`[${mockGraphQLType}!]!`)
            expect(config.fields[`${mockTargetTypeName}s`].resolve).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].subscribe).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].deprecationReason).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].extensions).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].astNode).to.be.undefined
          })

          describe('Array with properties', () => {
            let mockPropertyName: string
            let mockTargetType: any
            let mockPropertyType: any

            beforeEach(() => {
              // Provision target types
              mockPropertyName = random.alphaNumeric(10)
              mockTargetType = Array
              mockTargetTypeName = mockTargetType.name
              mockPropertyType = Boolean
              const mockedClassMetadata = {
                name: mockTargetTypeName,
                type: mockTargetType,
                fields: [
                  {
                    name: mockPropertyName,
                    typeInfo: {
                      name: mockPropertyType.name,
                      typeGroup: 'Boolean',
                      type: mockPropertyType,
                      parameters: [],
                      isNullable: false,
                    },
                  },
                ],
                methods: [
                  {
                    name: '',
                    typeInfo: {
                      name: mockPropertyType.name,
                      typeGroup: 'String',
                      type: mockPropertyType,
                      parameters: [],
                      isNullable: false,
                    },
                  },
                ],
              } as ClassMetadata
              getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)
            })

            context('Property GraphQL Type is scalar', () => {
              beforeEach(() => {
                sut = new GraphQLQueryGenerator(
                  simpleConfig,
                  [mockTargetType],
                  mockTypeInformer as any,
                  mockByIdResolverBuilder,
                  mockFilterResolverBuilder,
                  mockEventsResolver
                )
              })

              it('should call typeInformer.getGraphQLTypeFor 3 times', () => {
                sut.generate()

                expect(getGraphQLTypeForStub).callCount(3).and.calledWith(mockTargetType)
              })

              it('should call filterResolverBuilder once with expected arguments', () => {
                sut.generate()

                expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(mockTargetType)
                // @ts-ignore
                expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
              })

              context('should have expected args', () => {
                it('When Boolean', () => {
                  const result = sut.generate()

                  const config: any = result.toConfig()
                  expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
                  expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.undefined

                  const booleansTypeFilterConfig =
                    config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName]
                  expect(booleansTypeFilterConfig.type.toString()).to.be.equal('BooleanPropertyFilter')

                  const fieldsKeys = Object.keys(booleansTypeFilterConfig.type.getFields())
                  expect(fieldsKeys).to.be.deep.equal(['eq', 'ne'])
                  expect(fieldsKeys.length).to.equal(2)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal('Boolean')
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.undefined
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })
                })

                it('When Number', () => {
                  mockPropertyName = random.alphaNumeric(10)
                  mockTargetType = Array
                  mockTargetTypeName = mockTargetType.name
                  mockPropertyType = Number
                  const mockedClassMetadata = {
                    name: mockTargetTypeName,
                    type: mockTargetType,
                    fields: [
                      {
                        name: mockPropertyName,
                        typeInfo: {
                          name: mockPropertyType.name,
                          typeGroup: 'Number',
                          type: mockPropertyType,
                          parameters: [],
                          isNullable: false,
                        },
                      },
                    ],
                    methods: [],
                  } as ClassMetadata
                  getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)

                  sut = new GraphQLQueryGenerator(
                    simpleConfig,
                    [mockTargetType],
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = sut.generate()

                  const config: any = result.toConfig()
                  expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
                  expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.undefined

                  const TypeFilterConfig =
                    config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName]
                  expect(TypeFilterConfig.type.toString()).to.be.equal('NumberPropertyFilter')

                  const fieldsKeys = Object.keys(TypeFilterConfig.type.getFields())
                  expect(fieldsKeys).to.be.deep.equal(['eq', 'ne', 'lte', 'lt', 'gte', 'gt', 'in'])
                  expect(fieldsKeys.length).to.equal(7)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    const type = fieldKey === 'in' ? '[Float]' : 'Float' // The in filter expects an array of the element
                    expect(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type)
                    expect(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })
                })

                it('When String', () => {
                  mockPropertyName = random.alphaNumeric(10)
                  mockTargetType = Array
                  mockTargetTypeName = mockTargetType.name
                  mockPropertyType = String
                  const mockedClassMetadata = {
                    name: mockTargetTypeName,
                    type: mockTargetType,
                    fields: [
                      {
                        name: mockPropertyName,
                        typeInfo: {
                          name: mockPropertyType.name,
                          typeGroup: 'String',
                          type: mockPropertyType,
                          parameters: [],
                          isNullable: false,
                        },
                      },
                    ],
                    methods: [],
                  } as ClassMetadata
                  getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)

                  sut = new GraphQLQueryGenerator(
                    simpleConfig,
                    [mockTargetType],
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = sut.generate()

                  const config: any = result.toConfig()
                  expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
                  expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.undefined

                  const TypeFilterConfig =
                    config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName]
                  expect(TypeFilterConfig.type.toString()).to.be.equal('StringPropertyFilter')

                  const fieldsKeys = Object.keys(TypeFilterConfig.type.getFields())
                  expect(fieldsKeys).to.be.deep.equal([
                    'eq',
                    'ne',
                    'lte',
                    'lt',
                    'gte',
                    'gt',
                    'in',
                    'beginsWith',
                    'contains',
                  ])
                  expect(fieldsKeys.length).to.equal(9)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    const type = fieldKey === 'in' ? '[String]' : 'String' // The in filter expects an array of the element
                    expect(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type)
                    expect(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })
                })
              })
            })
          })
        })

        context('Cannot filter type', () => {
          // TODO: Currently it is not possible to filter complex properties
        })
      })

      context('with a config with events and entities', () => {
        let sut: GraphQLQueryGenerator
        const entityNames = ['TestEntity1', 'TestEntity2', 'TestEntity3']
        const eventTypeNames = ['EventType1', 'EventType2', 'EventType3']
        let mockTargetTypeClass: BooleanConstructor

        beforeEach(() => {
          const configWithEventsAndEntities = new BoosterConfig('test')
          for (const entityName of entityNames) {
            configWithEventsAndEntities.entities[entityName] = {} as never
          }
          for (const eventTypeName of eventTypeNames) {
            configWithEventsAndEntities.reducers[eventTypeName] = {} as never
          }
          mockTargetTypeClass = Boolean
          const mockedClassMetadata = {
            name: mockTargetTypeClass.name,
            type: mockTargetTypeClass,
            fields: [],
            methods: [],
          } as ClassMetadata
          sinon
            .stub(metadata, 'getClassMetadata')
            .withArgs(mockTargetTypeClass)
            .returns(mockedClassMetadata as ClassMetadata)

          sut = new GraphQLQueryGenerator(
            configWithEventsAndEntities,
            [mockTargetTypeClass],
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder,
            mockEventsResolver
          )
        })

        it('should return expected result', () => {
          const result = sut.generate().toConfig()
          expect(result.name).to.be.equal('Query')
          expect(new Set(Object.keys(result.fields))).to.be.deep.equal(new Set(['Boolean', 'Booleans', 'ListBooleans', 'eventsByEntity', 'eventsByType']))

          const eventsByEntityField = result.fields['eventsByEntity']
          expect(new Set(Object.keys(eventsByEntityField.args!))).to.be.deep.equal(
            new Set(['entity', 'entityID', 'from', 'to'])
          )
          const entityEnumType = (eventsByEntityField.args!['entity'].type as GraphQLNonNull<GraphQLEnumType>).ofType
          expect(new Set(entityEnumType.getValues().map((v) => v.value))).to.be.deep.equal(new Set(entityNames))
          assertEventSearchQueryReturnType(eventsByEntityField.type)

          const eventsByType = result.fields['eventsByType']
          expect(new Set(Object.keys(eventsByType.args!))).to.be.deep.equal(new Set(['type', 'from', 'to']))
          const eventTypeEnumType = (eventsByType.args!['type'].type as GraphQLNonNull<GraphQLEnumType>).ofType
          expect(new Set(eventTypeEnumType.getValues().map((v) => v.value))).to.be.deep.equal(new Set(eventTypeNames))
          assertEventSearchQueryReturnType(eventsByType.type)
        })

        function assertEventSearchQueryReturnType(queryReturnType: GraphQLOutputType): void {
          expect(queryReturnType).to.be.instanceOf(GraphQLList)
          const returnElementType = (queryReturnType as GraphQLList<GraphQLObjectType>).ofType
          expect(returnElementType.name).to.be.equal('EventQueryResponse')
          expect(new Set(Object.keys(returnElementType.getFields()))).to.be.deep.equal(
            new Set(['type', 'entity', 'requestID', 'entityID', 'user', 'createdAt', 'value'])
          )
          const userType = returnElementType.getFields()['user'].type as GraphQLObjectType
          expect(new Set(Object.keys(userType.getFields()))).to.be.deep.equal(new Set(['id', 'username', 'roles']))
        }
      })

      context('without target types', () => {
        let sut: GraphQLQueryGenerator
        beforeEach(() => {
          sut = new GraphQLQueryGenerator(
            simpleConfig,
            [],
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder,
            mockEventsResolver
          )
        })

        it('should not call typeInformer.getGraphQLTypeFor', () => {
          sut.generate()

          expect(getGraphQLTypeForStub).to.not.be.called
        })

        it('should return only the event queries', () => {
          const result = sut.generate()

          expect(result.name).to.be.equal('Query')

          const config = result.toConfig()
          expect(Object.keys(config.fields).length).to.eq(2)
          expect(config.fields).to.include.keys('eventsByEntity', 'eventsByType')
        })
      })
    })
  })

  describe('the `generateFilterQueries` private method', () => {
    it(
      'generates a query named after the plural version of the read model name that responds with a list of read model instances'
    ) // TODO
  })

  describe('the `generateListedQueries` private method', () => {
    it(
      'generates a query named List<PluralizedNameOfReadModel> that responds with a paginated list of read model instances'
    ) // TODO
  })

  describe('the `generateEventsQueries` private method', () => {
    it('generates event queries for specific entities') // TODO
  })
})
