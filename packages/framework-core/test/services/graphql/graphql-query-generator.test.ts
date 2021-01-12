import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { SinonStub, stub, replace, SinonStubbedInstance, restore, createStubInstance } from 'sinon'
import { expect } from '../../expect'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import { TargetTypesMap } from '../../../src/services/graphql/common'
import { GraphQLBoolean, GraphQLFloat, GraphQLID, GraphQLInt, GraphQLString } from 'graphql'
import { random } from 'faker'
import GraphQLJSON, { GraphQLJSONObject } from 'graphql-type-json'

describe('GraphQLQueryGenerator', () => {
  let mockTargetTypes: TargetTypesMap
  let mockGraphQLType: any

  let mockTypeInformer: SinonStubbedInstance<GraphQLTypeInformer>
  let mockByIdResolverBuilder: SinonStub
  let mockFilterResolverBuilder: SinonStub

  let getGraphQLTypeForStub: SinonStub

  let sut: GraphQLQueryGenerator

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

    mockTypeInformer = createStubInstance(GraphQLTypeInformer)
    mockByIdResolverBuilder = stub()
    mockFilterResolverBuilder = stub()

    getGraphQLTypeForStub = stub().returns(mockGraphQLType)
    replace(mockTypeInformer, 'getGraphQLTypeFor', getGraphQLTypeForStub as any)

    sut = new GraphQLQueryGenerator(
      mockTargetTypes,
      mockTypeInformer as any,
      mockByIdResolverBuilder,
      mockFilterResolverBuilder
    )
  })

  afterEach(() => {
    restore()
  })

  describe('generate', () => {
    describe('with target types', () => {
      context('1 target type', () => {
        let mockTargetTypeClass: BooleanConstructor | StringConstructor | NumberConstructor
        let mockTargetTypeName: string

        beforeEach(() => {
          mockTargetTypeClass = random.arrayElement([Boolean, String, Number])
          mockTargetTypeName = mockTargetTypeClass.name.toLowerCase()

          // Provision target types
          mockTargetTypes = {}
          mockTargetTypes[mockTargetTypeName] = {
            class: mockTargetTypeClass,
            properties: [],
          }

          sut = new GraphQLQueryGenerator(
            mockTargetTypes,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder
          )
        })

        it('should call typeInformer.getGraphQLTypeFor twice', () => {
          sut.generate()

          expect(getGraphQLTypeForStub).calledTwice.and.calledWith(mockTargetTypeClass)
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
          expect(config.fields[`${mockTargetTypeName}s`].type.toString()).to.be.equal(`[${mockGraphQLType}]`)
          expect(config.fields[`${mockTargetTypeName}s`].resolve).to.be.undefined
          expect(config.fields[`${mockTargetTypeName}s`].subscribe).to.be.undefined
          expect(config.fields[`${mockTargetTypeName}s`].deprecationReason).to.be.undefined
          expect(config.fields[`${mockTargetTypeName}s`].extensions).to.be.undefined
          expect(config.fields[`${mockTargetTypeName}s`].astNode).to.be.undefined
        })

        describe('with properties', () => {
          let mockPropertyName: string
          let mockTargetType: any
          let mockPropertyType: any

          beforeEach(() => {
            // Provision target types
            mockPropertyName = random.alphaNumeric(10)
            mockTargetType = Boolean
            mockPropertyType = Boolean
            mockTargetTypes = {}
            mockTargetTypes[mockTargetTypeName] = {
              class: mockTargetType,
              properties: [
                {
                  name: mockPropertyName,
                  type: mockPropertyType,
                },
              ],
            }
          })

          context('Property GraphQL Type is scalar', () => {
            beforeEach(() => {
              sut = new GraphQLQueryGenerator(
                mockTargetTypes,
                mockTypeInformer as any,
                mockByIdResolverBuilder,
                mockFilterResolverBuilder
              )
            })

            it('should call typeInformer.getGraphQLTypeFor 4 times', () => {
              sut.generate()

              expect(getGraphQLTypeForStub)
                .callCount(4)
                .and.calledWith(mockTargetType)
                .and.calledWith(mockPropertyType)
            })

            it('should call filterResolverBuilder once with expected arguments', () => {
              sut.generate()

              expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(Boolean)
              // @ts-ignore
              expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
            })

            it('should have expected args', () => {
              const result = sut.generate()

              const config: any = result.toConfig()
              expect(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!')
              expect(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined
              expect(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined
              expect(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined
              expect(config.fields[mockTargetTypeName].args['id'].extensions).to.be.undefined

              const booleansTypeFilterConfig = config.fields[`${mockTargetTypeName}s`].args[
                mockPropertyName
              ].type.toConfig()
              expect(booleansTypeFilterConfig.name).to.be.equal('BooleanPropertyFilter')
              expect(booleansTypeFilterConfig.fields.operation.description).to.be.undefined
              expect(booleansTypeFilterConfig.fields.operation.type.toString()).to.be.equal('BooleanOperations!')
              expect(booleansTypeFilterConfig.fields.operation.defaultValue).to.be.undefined
              expect(booleansTypeFilterConfig.fields.operation.extensions).to.be.undefined
              expect(booleansTypeFilterConfig.fields.operation.astNode).to.be.undefined
              expect(booleansTypeFilterConfig.fields.values.description).to.be.undefined
              expect(booleansTypeFilterConfig.fields.values.type.toString()).to.be.equal(
                `[${mockGraphQLType.toString()}!]!`
              )
              expect(booleansTypeFilterConfig.fields.values.defaultValue).to.be.undefined
              expect(booleansTypeFilterConfig.fields.values.extensions).to.be.undefined
              expect(booleansTypeFilterConfig.fields.values.astNode).to.be.undefined
            })
          })

          context('Property GraphQL Type is GraphQLJSONObject', () => {
            beforeEach(() => {
              getGraphQLTypeForStub.returns(GraphQLJSONObject)

              sut = new GraphQLQueryGenerator(
                mockTargetTypes,
                mockTypeInformer as any,
                mockByIdResolverBuilder,
                mockFilterResolverBuilder
              )
            })

            it('should call typeInformer.getGraphQLTypeFor 3 times', () => {
              sut.generate()

              expect(getGraphQLTypeForStub)
                .callCount(3)
                .and.calledWith(mockTargetType)
                .and.calledWith(mockPropertyType)
            })

            it('should call filterResolverBuilder once with expected arguments', () => {
              sut.generate()

              expect(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(Boolean)
              // @ts-ignore
              expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
            })
          })
        })
      })

      context('several target types', () => {
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
            mockTargetTypes,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder
          )
        })

        it('should call typeInformer.getGraphQLTypeFor n of target types * 2', () => {
          sut.generate()

          expect(getGraphQLTypeForStub)
            .callCount(4)
            .and.calledWith(Boolean)
            .and.calledWith(String)
        })

        it('should call filterResolverBuilder twice with expected arguments', () => {
          sut.generate()

          expect(mockByIdResolverBuilder)
            .to.be.calledTwice.and.calledWith(Boolean)
            .and.calledWith(String)
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
              mockTargetTypes,
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockFilterResolverBuilder
            )
          })

          it('should call typeInformer.getGraphQLTypeFor (number of types * 2) - (2 * (repeated types))', () => {
            sut.generate()

            expect(getGraphQLTypeForStub)
              .to.be.callCount(4)
              .and.calledWith(Boolean)
              .and.calledWith(String)
          })

          it('should call filterResolverBuilder twice with expected arguments', () => {
            sut.generate()

            expect(mockByIdResolverBuilder)
              .to.be.calledTwice.and.calledWith(Boolean)
              .and.calledWith(String)
            // @ts-ignore
            expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
          })
        })
      })

      context('Cannot filter type', () => {
        // TODO: Currently it is not possible to filter complex properties
      })
    })

    describe('without target types', () => {
      it('should not call typeInformer.getGraphQLTypeFor', () => {
        sut.generate()

        expect(getGraphQLTypeForStub).to.not.be.called
      })

      it('should return expected result', () => {
        const result = sut.generate()

        expect(result.name).to.be.equal('Query')
        expect(result.description).to.be.undefined
        expect(result.extensions).to.be.undefined
        expect(result.astNode).to.be.undefined
        expect(result.extensionASTNodes).to.be.undefined

        const config: any = result.toConfig()
        expect(config.fields).to.be.deep.equal({})
      })
    })
  })
})
