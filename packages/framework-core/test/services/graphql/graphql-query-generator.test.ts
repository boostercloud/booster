/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { SinonStub, stub, replace, SinonStubbedInstance, restore } from 'sinon'
import { expect } from '../../expect'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import sinon = require('sinon')
import { TargetTypesMap } from '../../../src/services/graphql/common'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLFloat,
  GraphQLID,
  GraphQLInt,
  GraphQLNonNull,
  GraphQLOutputType,
  GraphQLString,
  GraphQLList,
  GraphQLObjectType,
} from 'graphql'
import { random } from 'faker'
import GraphQLJSON, { GraphQLJSONObject } from 'graphql-type-json'
import { BoosterConfig } from '@boostercloud/framework-types'

describe('GraphQLQueryGenerator', () => {
  afterEach(() => {
    restore()
  })

  describe('the `generate` public method', () => {
    const simpleConfig = new BoosterConfig('test')

    context('black box tests', () => {
      let mockTargetTypes: TargetTypesMap
      let mockGraphQLType: any

      let mockTypeInformer: SinonStubbedInstance<GraphQLTypeInformer>
      let mockByIdResolverBuilder: SinonStub
      let mockFilterResolverBuilder: SinonStub
      let mockEventsResolver: SinonStub

      let getGraphQLTypeForStub: SinonStub
      let isGraphQLScalarTypeStub: SinonStub

      beforeEach(() => {
        mockTargetTypes = {}
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
        replace(mockTypeInformer, 'getGraphQLTypeFor', getGraphQLTypeForStub as any)
        isGraphQLScalarTypeStub = stub().returns(mockGraphQLType)
        replace(mockTypeInformer, 'isGraphQLScalarType', isGraphQLScalarTypeStub as any)
      })

      context('with target types', () => {
        context('1 target type', () => {
          let mockTargetTypeClass: BooleanConstructor | StringConstructor | NumberConstructor
          let mockTargetTypeName: string
          let graphQLQueryGenerator: GraphQLQueryGenerator

          beforeEach(() => {
            mockTargetTypeClass = random.arrayElement([Boolean, String, Number])
            mockTargetTypeName = mockTargetTypeClass.name.toLowerCase()

            // Provision target types
            mockTargetTypes = {}
            mockTargetTypes[mockTargetTypeName] = {
              class: mockTargetTypeClass,
              properties: [],
            }

            graphQLQueryGenerator = new GraphQLQueryGenerator(
              simpleConfig,
              mockTargetTypes,
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockFilterResolverBuilder,
              mockEventsResolver
            )
          })

          it('should call typeInformer.getGraphQLTypeFor thrice', () => {
            graphQLQueryGenerator.generate()

            expect(getGraphQLTypeForStub).calledThrice.and.calledWith(mockTargetTypeClass)
          })

          it('should call filterResolverBuilder once with expected argument', () => {
            graphQLQueryGenerator.generate()

            expect(mockByIdResolverBuilder).calledOnce.and.calledWith(mockTargetTypeClass)
            // @ts-ignore
            expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
          })

          it('should return expected result', () => {
            const result = graphQLQueryGenerator.generate()

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
            expect(config.fields[`${mockTargetTypeName}s`].type.toString()).to.be.equal(`[${mockGraphQLType}]`)
            expect(config.fields[`${mockTargetTypeName}s`].resolve).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].subscribe).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].deprecationReason).to.be.equal(
              'Method is deprecated. Use List* methods'
            )
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
              mockPropertyType = Boolean
              mockTargetTypes = {}
              mockTargetTypes[mockTargetTypeName] = {
                class: mockTargetType,
                properties: [
                  {
                    name: mockPropertyName,
                    typeInfo: {
                      name: mockPropertyType.name,
                      type: mockPropertyType,
                      parameters: [],
                    },
                  },
                ],
              }
              replace(mockTypeInformer, 'getOriginalAncestor', stub().returnsArg(0) as any)
            })

            context('Property GraphQL Type is scalar', () => {
              beforeEach(() => {
                graphQLQueryGenerator = new GraphQLQueryGenerator(
                  simpleConfig,
                  mockTargetTypes,
                  mockTypeInformer as any,
                  mockByIdResolverBuilder,
                  mockFilterResolverBuilder,
                  mockEventsResolver
                )
              })

              it('should call typeInformer.getGraphQLTypeFor 5 times', () => {
                graphQLQueryGenerator.generate()

                expect(getGraphQLTypeForStub)
                  .callCount(5)
                  .and.calledWith(mockTargetType)
                  .and.calledWith(mockPropertyType)
              })

              it('should call filterResolverBuilder once with expected arguments', () => {
                graphQLQueryGenerator.generate()

                expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(mockTargetType)
                // @ts-ignore
                expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
              })

              context('should have expected args', () => {
                it('When Boolean', () => {
                  const result = graphQLQueryGenerator.generate()

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

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.undefined
                  expect(sortBy.astNode).to.be.undefined
                  const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName]
                  expect(booleansTypeSortConfig.type.toString()).to.not.be.undefined
                  expect(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`)
                  const sortKeys = Object.keys(sortBy.type.getFields())
                  sortKeys.forEach((fieldKey) => {
                    expect(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty')
                    const values = sortBy.type
                      .getFields()
                      [fieldKey].type.getValues()
                      .map((value: { name: any }) => value.name)
                    expect(values).to.be.eql(['ASC', 'DESC'])
                  })
                })

                it('When Number', () => {
                  mockPropertyName = random.alphaNumeric(10)
                  mockTargetType = Array
                  mockPropertyType = Number
                  mockTargetTypes = {}
                  mockTargetTypes[mockTargetTypeName] = {
                    class: mockTargetType,
                    properties: [
                      {
                        name: mockPropertyName,
                        typeInfo: {
                          name: mockPropertyType.name,
                          type: mockPropertyType,
                          parameters: [],
                        },
                      },
                    ],
                  }
                  graphQLQueryGenerator = new GraphQLQueryGenerator(
                    simpleConfig,
                    mockTargetTypes,
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = graphQLQueryGenerator.generate()

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

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.undefined
                  expect(sortBy.astNode).to.be.undefined
                  const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName]
                  expect(booleansTypeSortConfig.type.toString()).to.not.be.undefined
                  expect(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`)
                  const sortKeys = Object.keys(sortBy.type.getFields())
                  sortKeys.forEach((fieldKey) => {
                    expect(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty')
                    const values = sortBy.type
                      .getFields()
                      [fieldKey].type.getValues()
                      .map((value: { name: any }) => value.name)
                    expect(values).to.be.eql(['ASC', 'DESC'])
                  })
                })

                it('When String', () => {
                  mockPropertyName = random.alphaNumeric(10)
                  mockTargetType = Array
                  mockPropertyType = String
                  mockTargetTypes = {}
                  mockTargetTypes[mockTargetTypeName] = {
                    class: mockTargetType,
                    properties: [
                      {
                        name: mockPropertyName,
                        typeInfo: {
                          name: mockPropertyType.name,
                          type: mockPropertyType,
                          parameters: [],
                        },
                      },
                    ],
                  }
                  graphQLQueryGenerator = new GraphQLQueryGenerator(
                    simpleConfig,
                    mockTargetTypes,
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = graphQLQueryGenerator.generate()

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

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.undefined
                  expect(sortBy.astNode).to.be.undefined
                  const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName]
                  expect(booleansTypeSortConfig.type.toString()).to.not.be.undefined
                  expect(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`)
                  const sortKeys = Object.keys(sortBy.type.getFields())
                  sortKeys.forEach((fieldKey) => {
                    expect(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty')
                    const values = sortBy.type
                      .getFields()
                      [fieldKey].type.getValues()
                      .map((value: { name: any }) => value.name)
                    expect(values).to.be.eql(['ASC', 'DESC'])
                  })
                })
              })
            })

            context('Property GraphQL Type is GraphQLJSONObject', () => {
              beforeEach(() => {
                class MockedClass {}
                getGraphQLTypeForStub.returns(GraphQLJSONObject)
                isGraphQLScalarTypeStub.returns(false)
                mockPropertyName = random.alphaNumeric(10)
                mockTargetType = Array
                mockPropertyType = MockedClass
                mockTargetTypes = {}
                mockTargetTypes[mockTargetTypeName] = {
                  class: mockTargetType,
                  properties: [
                    {
                      name: mockPropertyName,
                      typeInfo: {
                        name: mockPropertyType.name,
                        type: mockPropertyType,
                        parameters: [],
                      },
                    },
                  ],
                }

                graphQLQueryGenerator = new GraphQLQueryGenerator(
                  simpleConfig,
                  mockTargetTypes,
                  mockTypeInformer as any,
                  mockByIdResolverBuilder,
                  mockFilterResolverBuilder,
                  mockEventsResolver
                )
              })

              it('should call typeInformer.getGraphQLTypeFor 6 times', () => {
                graphQLQueryGenerator.generate()

                expect(getGraphQLTypeForStub)
                  .callCount(6)
                  .and.calledWith(mockTargetType)
                  .and.calledWith(mockPropertyType)
              })

              it('should call filterResolverBuilder once with expected arguments', () => {
                graphQLQueryGenerator.generate()

                expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(mockTargetType)
                // @ts-ignore
                expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
              })
            })

            context('Property GraphQL Type is GraphQLString', () => {
              beforeEach(() => {
                getGraphQLTypeForStub.returns(GraphQLString)
                mockPropertyName = random.alphaNumeric(10)
                mockTargetType = Array
                mockPropertyType = String
                mockTargetTypes = {}
                mockTargetTypes[mockTargetTypeName] = {
                  class: mockTargetType,
                  properties: [
                    {
                      name: mockPropertyName,
                      typeInfo: {
                        name: mockPropertyType.name,
                        type: mockPropertyType,
                        parameters: [],
                      },
                    },
                  ],
                }

                graphQLQueryGenerator = new GraphQLQueryGenerator(
                  simpleConfig,
                  mockTargetTypes,
                  mockTypeInformer as any,
                  mockByIdResolverBuilder,
                  mockFilterResolverBuilder,
                  mockEventsResolver
                )
              })

              it('should call typeInformer.getGraphQLTypeFor 5 times', () => {
                graphQLQueryGenerator.generate()

                expect(getGraphQLTypeForStub)
                  .callCount(5)
                  .and.calledWith(mockTargetType)
                  .and.calledWith(mockPropertyType)
              })

              it('should call filterResolverBuilder once with expected arguments', () => {
                graphQLQueryGenerator.generate()

                expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(mockTargetType)
                // @ts-ignore
                expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
              })
            })
          })
        })

        context('several target types', () => {
          let sut: GraphQLQueryGenerator
          beforeEach(() => {
            // Provision target types
            mockTargetTypes = {}
            mockTargetTypes['boolean'] = {
              class: Boolean,
              properties: [],
            }
            mockTargetTypes['string'] = {
              class: String,
              properties: [],
            }

            sut = new GraphQLQueryGenerator(
              simpleConfig,
              mockTargetTypes,
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockFilterResolverBuilder,
              mockEventsResolver
            )
          })

          it('should call typeInformer.getGraphQLTypeFor n of target types * 2', () => {
            sut.generate()

            expect(getGraphQLTypeForStub).callCount(6).and.calledWith(Boolean).and.calledWith(String)
          })

          it('should call filterResolverBuilder twice with expected arguments', () => {
            sut.generate()

            expect(mockByIdResolverBuilder).to.be.calledTwice.and.calledWith(Boolean).and.calledWith(String)
            // @ts-ignore
            expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
          })

          describe('repeated type', () => {
            beforeEach(() => {
              // Provision target types
              mockTargetTypes = {}
              mockTargetTypes['boolean'] = {
                class: Boolean,
                properties: [],
              }
              mockTargetTypes['string'] = {
                class: String,
                properties: [],
              }
              mockTargetTypes['string'] = {
                class: String,
                properties: [],
              }

              sut = new GraphQLQueryGenerator(
                simpleConfig,
                mockTargetTypes,
                mockTypeInformer as any,
                mockByIdResolverBuilder,
                mockFilterResolverBuilder,
                mockEventsResolver
              )
            })

            it('should call typeInformer.getGraphQLTypeFor (number of types * 2) - (2 * (repeated types))', () => {
              sut.generate()

              expect(getGraphQLTypeForStub).to.be.callCount(6).and.calledWith(Boolean).and.calledWith(String)
            })

            it('should call filterResolverBuilder twice with expected arguments', () => {
              sut.generate()

              expect(mockByIdResolverBuilder).to.be.calledTwice.and.calledWith(Boolean).and.calledWith(String)
              // @ts-ignore
              expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
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

        beforeEach(() => {
          const configWithEventsAndEntities = new BoosterConfig('test')
          for (const entityName of entityNames) {
            configWithEventsAndEntities.entities[entityName] = {} as never
          }
          for (const eventTypeName of eventTypeNames) {
            configWithEventsAndEntities.reducers[eventTypeName] = {} as never
          }
          sut = new GraphQLQueryGenerator(
            configWithEventsAndEntities,
            mockTargetTypes,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder,
            mockEventsResolver
          )
        })

        it('should return expected result', () => {
          const result = sut.generate().toConfig()
          expect(result.name).to.be.equal('Query')
          expect(new Set(Object.keys(result.fields))).to.be.deep.equal(new Set(['eventsByEntity', 'eventsByType']))

          const eventsByEntityField = result.fields['eventsByEntity']
          expect(new Set(Object.keys(eventsByEntityField.args!))).to.be.deep.equal(
            new Set(['entity', 'entityID', 'from', 'to', 'limit'])
          )
          const entityEnumType = (eventsByEntityField.args!['entity'].type as GraphQLNonNull<GraphQLEnumType>).ofType
          expect(new Set(entityEnumType.getValues().map((v) => v.value))).to.be.deep.equal(new Set(entityNames))
          assertEventSearchQueryReturnType(eventsByEntityField.type)

          const eventsByType = result.fields['eventsByType']
          expect(new Set(Object.keys(eventsByType.args!))).to.be.deep.equal(new Set(['type', 'from', 'to', 'limit']))
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
            mockTargetTypes,
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
