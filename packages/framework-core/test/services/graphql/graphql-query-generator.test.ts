/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { SinonStub, stub, replace, SinonStubbedInstance, restore } from 'sinon'
import { expect } from '../../expect'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import sinon = require('sinon')
import {
  GraphQLBoolean,
  GraphQLFloat,
  GraphQLID,
  GraphQLInt,
  GraphQLString,
  GraphQLNonNull,
  GraphQLEnumType,
  GraphQLOutputType,
  GraphQLObjectType,
  GraphQLList,
} from 'graphql'
import { random } from 'faker'
import { GraphQLJSON } from 'graphql-scalars'
import { AnyClass, BoosterConfig, QueryMetadata } from '@boostercloud/framework-types'
import { ClassMetadata } from '@boostercloud/metadata-booster'
import * as metadata from '../../../src/decorators/metadata'

describe('GraphQLQueryGenerator', () => {
  afterEach(() => {
    restore()
  })

  describe('the `generate` public method', () => {
    const simpleConfig = new BoosterConfig('test')

    context('black box tests', () => {
      let mockGraphQLType: any

      let mockTypeInformer: SinonStubbedInstance<GraphQLTypeInformer>
      let mockByIdResolverBuilder: SinonStub
      let mockQueryResolverBuilder: SinonStub
      let mockFilterResolverBuilder: SinonStub
      let mockEventsResolver: SinonStub
      const queryHandlers: Record<string, QueryMetadata> = {}

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
        mockQueryResolverBuilder = stub()
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
          let graphQLQueryGenerator: GraphQLQueryGenerator
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

            graphQLQueryGenerator = new GraphQLQueryGenerator(
              simpleConfig,
              [mockTargetTypeClass],
              queryHandlers,
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockQueryResolverBuilder,
              mockFilterResolverBuilder,
              mockEventsResolver
            )

            getClassMetadataStub = sinon
              .stub(metadata, 'getClassMetadata')
              .withArgs(mockTargetTypeClass)
              .returns(mockedClassMetadata as ClassMetadata)
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
            expect(result.extensions).to.be.empty
            expect(result.astNode).to.be.undefined
            expect(result.extensionASTNodes).to.be.empty

            const config: any = result.toConfig()
            expect(config.fields[mockTargetTypeName].description).to.be.undefined
            expect(config.fields[mockTargetTypeName].type).to.be.equal(mockGraphQLType)
            expect(config.fields[mockTargetTypeName].resolve).to.be.undefined
            expect(config.fields[mockTargetTypeName].subscribe).to.be.undefined
            expect(config.fields[mockTargetTypeName].deprecationReason).to.be.undefined
            expect(config.fields[mockTargetTypeName].extensions).to.be.empty
            expect(config.fields[mockTargetTypeName].astNode).to.be.undefined

            expect(config.fields[`${mockTargetTypeName}s`].description).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].type.toString()).to.be.equal(`[${mockGraphQLType}!]!`)
            expect(config.fields[`${mockTargetTypeName}s`].resolve).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].subscribe).to.be.undefined
            expect(config.fields[`${mockTargetTypeName}s`].deprecationReason).to.be.equal(
              'Method is deprecated. Use List* methods'
            )
            expect(config.fields[`${mockTargetTypeName}s`].extensions).to.be.empty
            expect(config.fields[`${mockTargetTypeName}s`].astNode).to.be.undefined
          })

          describe('Array with properties', () => {
            let mockPropertyName: string
            let mockTargetType: any
            let mockPropertyType: any

            beforeEach(() => {
              // Provision target types
              mockPropertyName = '_a' + random.alphaNumeric(10)
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
                      typeName: 'Boolean',
                      parameters: [],
                      isNullable: false,
                      isGetAccessor: false,
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
                      typeName: 'String',
                      isNullable: false,
                      isGetAccessor: false,
                    },
                  },
                ],
              } as ClassMetadata
              getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)
            })

            context('Property GraphQL Type is scalar', () => {
              beforeEach(() => {
                graphQLQueryGenerator = new GraphQLQueryGenerator(
                  simpleConfig,
                  [mockTargetType],
                  queryHandlers,
                  mockTypeInformer as any,
                  mockByIdResolverBuilder,
                  mockQueryResolverBuilder,
                  mockFilterResolverBuilder,
                  mockEventsResolver
                )
              })

              it('should call typeInformer.getGraphQLTypeFor 3 times', () => {
                graphQLQueryGenerator.generate()

                expect(getGraphQLTypeForStub).callCount(3).and.calledWith(mockTargetType)
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
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty

                  const booleansTypeFilterConfig =
                    config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName]
                  expect(booleansTypeFilterConfig.type.toString()).to.be.equal('BooleanPropertyFilter')

                  const fieldsKeys = Object.keys(booleansTypeFilterConfig.type.getFields())
                  expect(fieldsKeys).to.be.deep.equal(['eq', 'ne', 'isDefined'])
                  expect(fieldsKeys.length).to.equal(3)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal('Boolean')
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty
                    expect(booleansTypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.empty
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
                  mockPropertyName = '_a' + random.alphaNumeric(10)
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
                          typeName: 'Number',
                          parameters: [],
                          isNullable: false,
                          isGetAccessor: false,
                        },
                      },
                    ],
                    methods: [],
                  } as ClassMetadata
                  getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)

                  graphQLQueryGenerator = new GraphQLQueryGenerator(
                    simpleConfig,
                    [mockTargetType],
                    queryHandlers,
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockQueryResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = graphQLQueryGenerator.generate()

                  const config: any = result.toConfig()
                  expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
                  expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty

                  const TypeFilterConfig =
                    config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName]
                  expect(TypeFilterConfig.type.toString()).to.be.equal('NumberPropertyFilter')

                  const fieldsKeys = Object.keys(TypeFilterConfig.type.getFields())
                  expect(fieldsKeys).to.be.deep.equal(['eq', 'ne', 'lte', 'lt', 'gte', 'gt', 'in', 'isDefined'])
                  expect(fieldsKeys.length).to.equal(8)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    let type: string
                    if (fieldKey === 'in') {
                      type = '[Float]'
                    } else if (fieldKey === 'isDefined') {
                      type = 'Boolean'
                    } else {
                      type = 'Float'
                    } // The in filter expects an array of the element
                    expect(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type)
                    expect(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty
                    expect(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.empty
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
                  mockPropertyName = '_a' + random.alphaNumeric(10)
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
                          typeName: 'String',
                          parameters: [],
                          isNullable: false,
                          isGetAccessor: false,
                        },
                      },
                    ],
                    methods: [],
                  } as ClassMetadata
                  getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata as ClassMetadata)

                  graphQLQueryGenerator = new GraphQLQueryGenerator(
                    simpleConfig,
                    [mockTargetType],
                    queryHandlers,
                    mockTypeInformer as any,
                    mockByIdResolverBuilder,
                    mockQueryResolverBuilder,
                    mockFilterResolverBuilder,
                    mockEventsResolver
                  )
                  const result = graphQLQueryGenerator.generate()

                  const config: any = result.toConfig()
                  expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
                  expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
                  expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty

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
                    'regex',
                    'iRegex',
                    'isDefined',
                  ])
                  expect(fieldsKeys.length).to.equal(12)

                  fieldsKeys.forEach((fieldKey) => {
                    expect(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined
                    let type: string
                    if (fieldKey === 'in') {
                      type = '[String]'
                    } else if (fieldKey === 'isDefined') {
                      type = 'Boolean'
                    } else {
                      type = 'String'
                    } // The in filter expects an array of the element
                    expect(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type)
                    expect(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined
                    expect(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty
                    expect(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined
                  })

                  const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy
                  expect(sortBy.description).to.be.undefined
                  expect(sortBy.defaultValue).to.be.undefined
                  expect(sortBy.deprecationReason).to.be.undefined
                  expect(sortBy.extensions).to.be.empty
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
          })
        })
      })

      context('with a config with events and entities', () => {
        let graphQLQueryGenerator: GraphQLQueryGenerator
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

          graphQLQueryGenerator = new GraphQLQueryGenerator(
            configWithEventsAndEntities,
            [mockTargetTypeClass],
            queryHandlers,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockQueryResolverBuilder,
            mockFilterResolverBuilder,
            mockEventsResolver
          )
        })

        it('should return expected result', () => {
          const result = graphQLQueryGenerator.generate().toConfig()
          expect(result.name).to.be.equal('Query')
          expect(new Set(Object.keys(result.fields))).to.be.deep.equal(
            new Set(['Boolean', 'Booleans', 'ListBooleans', 'eventsByEntity', 'eventsByType'])
          )

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
        let graphQLQueryGenerator: GraphQLQueryGenerator
        beforeEach(() => {
          graphQLQueryGenerator = new GraphQLQueryGenerator(
            simpleConfig,
            [],
            queryHandlers,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockQueryResolverBuilder,
            mockFilterResolverBuilder,
            mockEventsResolver
          )
        })

        it('should not call typeInformer.getGraphQLTypeFor', () => {
          graphQLQueryGenerator.generate()

          expect(getGraphQLTypeForStub).to.not.be.called
        })

        it('should return only the event queries', () => {
          const result = graphQLQueryGenerator.generate()

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
