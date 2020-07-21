import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import { AnyClass, UUID } from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputType,
  GraphQLList,
  GraphQLNonNull,
  GraphQLString,
} from 'graphql'
import { expect } from '../../expect'
import { GraphQLJSONObject } from 'graphql-type-json'
import { random } from 'faker'
import { SinonStub, stub, restore } from 'sinon'
import { GraphQLNonInputType } from '../../../src/services/graphql/common'

describe('GraphQLTypeInformer', () => {
  let sut: GraphQLTypeInformer

  beforeEach(() => {
    sut = new GraphQLTypeInformer({
      testClass: {
        class: {
          name: 'testClass',
        } as AnyClass,
        properties: {} as any,
      },
    })
  })

  describe('getGraphQLTypeFor', () => {
    context('without types by name', () => {
      beforeEach(() => {
        sut = new GraphQLTypeInformer({})
      })

      it('should return GraphQLID', () => {
        const result = sut.getGraphQLTypeFor(UUID)

        expect(result).to.be.equal(GraphQLID)
      })

      it('should return GraphQLString', () => {
        const result = sut.getGraphQLTypeFor(String)

        expect(result).to.be.equal(GraphQLString)
      })

      it('should return GraphQLFloat', () => {
        const result = sut.getGraphQLTypeFor(Number)

        expect(result).to.be.equal(GraphQLFloat)
      })

      it('should return GraphQLBoolean', () => {
        const result = sut.getGraphQLTypeFor(Boolean)

        expect(result).to.be.equal(GraphQLBoolean)
      })

      it('should return GraphQLList(GraphQLJSONObject)', () => {
        const result = sut.getGraphQLTypeFor(Array)

        expect(result).to.be.deep.equal(GraphQLList(GraphQLJSONObject))
      })

      it('should return GraphQLJSONObject', () => {
        const result = sut.getGraphQLTypeFor(Object)

        expect(result).to.be.deep.equal(GraphQLJSONObject)
      })

      describe('default', () => {
        let mockType: AnyClass

        beforeEach(() => {
          mockType = random.arrayElement([Float32Array, Float32Array, Uint8Array])
        })

        it('should return GraphQLJSONObject', () => {
          const result = sut.getGraphQLTypeFor(mockType)

          expect(result).to.be.deep.equal(GraphQLJSONObject)
        })
      })
    })
  })

  describe('getGraphQLInputTypeFor', () => {
    let mockType: AnyClass
    let mockGraphQLOutputType: GraphQLNonInputType
    let mockInputType: GraphQLInputType

    let toInputTypeStub: SinonStub
    let getGraphQLTypeForStub: SinonStub

    beforeEach(() => {
      mockType = random.arrayElement([String, Boolean, UUID, Object, Array])
      mockGraphQLOutputType = random.arrayElement([
        GraphQLID,
        GraphQLString,
        GraphQLFloat,
        GraphQLBoolean,
        GraphQLJSONObject,
      ])
      mockInputType = random.arrayElement([GraphQLString, GraphQLID, GraphQLFloat, GraphQLJSONObject, GraphQLBoolean])

      toInputTypeStub = stub(GraphQLTypeInformer.prototype, 'toInputType').returns(mockInputType)
      getGraphQLTypeForStub = stub(GraphQLTypeInformer.prototype, 'getGraphQLTypeFor').returns(mockGraphQLOutputType)
    })

    afterEach(() => {
      restore()
    })

    it('should call toInputType', () => {
      sut.getGraphQLInputTypeFor(mockType)

      expect(toInputTypeStub).to.be.calledOnce.and.calledWith(mockGraphQLOutputType)
    })

    it('should call getGraphQLTypeFor', () => {
      sut.getGraphQLInputTypeFor(mockType)

      expect(getGraphQLTypeForStub).to.be.calledOnce.and.calledWith(mockType)
    })

    it('should return expected result', () => {
      const result = sut.getGraphQLInputTypeFor(mockType)

      expect(result).to.be.equal(mockInputType)
    })
  })

  describe('toInputType', () => {
    describe('instance of GraphQLFloat', () => {
      it('should return the same input type', () => {
        const result = sut.toInputType(GraphQLFloat)

        expect(result).to.be.equal(GraphQLFloat)
      })
    })

    describe('instance of GraphQLJSONObject', () => {
      it('should return the same input type', () => {
        const result = sut.toInputType(GraphQLJSONObject)

        expect(result).to.be.equal(GraphQLJSONObject)
      })
    })

    describe('instance of GraphQLList', () => {
      it('should return the same input type', () => {
        const result = sut.toInputType(GraphQLList(GraphQLFloat))

        expect(result).to.be.deep.equal(GraphQLList(GraphQLFloat))
      })
    })

    describe('instance of GraphQLNonNull', () => {
      it('should return the same input type', () => {
        const result = sut.toInputType(GraphQLNonNull(GraphQLFloat))

        expect(result).to.be.deep.equal(GraphQLNonNull(GraphQLFloat))
      })
    })
  })
})
