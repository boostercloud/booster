import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { SinonStub, stub, replace, SinonStubbedInstance } from 'sinon'
import { expect } from '../../expect'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import sinon = require('sinon')
import { TargetTypesMap } from '../../../src/services/graphql/common'
import { GraphQLBoolean, GraphQLObjectType } from 'graphql'
import { random } from 'faker'

describe('GraphQLQueryGenerator', () => {
  let mockTargetTypes: TargetTypesMap
  let mockTypeInformer: SinonStubbedInstance<GraphQLTypeInformer>
  let mockByIdResolverBuilder: SinonStub
  let mockFilterResolverBuilder: SinonStub

  let getGraphQLTypeForStub: SinonStub

  let sut: GraphQLQueryGenerator

  beforeEach(() => {
    mockTargetTypes = {}
    mockTypeInformer = sinon.createStubInstance(GraphQLTypeInformer)
    mockByIdResolverBuilder = stub()
    mockFilterResolverBuilder = stub()

    getGraphQLTypeForStub = stub().returns(GraphQLBoolean)

    replace(mockTypeInformer, 'getGraphQLTypeFor', getGraphQLTypeForStub as any)

    sut = new GraphQLQueryGenerator(
      mockTargetTypes,
      mockTypeInformer as any,
      mockByIdResolverBuilder,
      mockFilterResolverBuilder
    )
  })

  describe('generate', () => {
    describe('with target types', () => {
      context('1 target type', () => {
        beforeEach(() => {
          // Provision target types
          mockTargetTypes = {
            boolean: {
              class: Boolean,
              properties: [],
            },
          }

          sut = new GraphQLQueryGenerator(
            mockTargetTypes,
            mockTypeInformer as any,
            mockByIdResolverBuilder,
            mockFilterResolverBuilder
          )
        })

        it('should call typeInformer.getGraphQLTypeFor twice times', () => {
          sut.generate()

          expect(getGraphQLTypeForStub).calledTwice.and.calledWith(Boolean)
        })

        it('should call filterResolverBuilder 1 time', () => {
          sut.generate()

          expect(mockByIdResolverBuilder).calledOnce.and.calledWith(Boolean)
          expect(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub)
        })

        it('should return expected result', () => {
          const expectedResult = {} as GraphQLObjectType

          const result = sut.generate()

          console.log(result)

          expect(result).to.deep.equal(expectedResult)
        })

        describe('with property', () => {
          let mockPropertyName: string
          let mockTargetType: any
          let mockPropertyType: any

          beforeEach(() => {
            // Provision target types
            mockPropertyName = random.alphaNumeric(10)
            mockTargetType = random.arrayElement([String, Boolean, Number])
            mockPropertyType = random.arrayElement([String, Boolean, Number])
            mockTargetTypes = {
              boolean: {
                class: mockTargetType,
                properties: [
                  {
                    name: mockPropertyName,
                    type: mockPropertyType,
                  },
                ],
              },
            }

            sut = new GraphQLQueryGenerator(
              mockTargetTypes,
              mockTypeInformer as any,
              mockByIdResolverBuilder,
              mockFilterResolverBuilder
            )
          })

          it('should call typeInformer.getGraphQLTypeFor 2 + # of properties * 2', () => {
            sut.generate()

            expect(getGraphQLTypeForStub)
              .callCount(4)
              .and.calledWith(mockTargetType)
              .and.calledWith(mockPropertyType)
          })

          it('should return expected result', () => {})
        })
      })

      context('several target types', () => {
        it('should call typeInformer.getGraphQLTypeFor n*4', () => {})

        describe('repeated type', () => {
          it('should call typeInformer.getGraphQLTypeFor n*4-(repeated types)', () => {})
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
    })
  })
})
