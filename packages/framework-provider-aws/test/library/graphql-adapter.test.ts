/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { GraphQLRequestEnvelope, ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { restore } from 'sinon'
import { random } from 'faker'
import { APIGatewayProxyEvent } from 'aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLList, GraphQLString } from 'graphql'

class TestReadModel implements ReadModelInterface {
  public constructor(readonly id: UUID, readonly testField: UUID[]) {}
}

describe('AWS Provider graphql-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawGraphQLRequestToEnvelope`', () => {
    // Existing test cases...
  })

  describe('getGraphQLTypeFor', () => {
    it('handles read models with T[] notation correctly', () => {
      const result = GraphQLAdapter.getGraphQLTypeFor(TestReadModel)
      const expectedType = new GraphQLObjectType({
        name: 'TestReadModel',
        fields: {
          id: { type: GraphQLString },
          testField: { type: new GraphQLList(GraphQLString) },
        },
      })
      expect(result).to.deep.equal(expectedType)
    })
  
    it('handles read models with Array<T> notation correctly', () => {
      class TestReadModelWithArrayT implements ReadModelInterface {
        public constructor(readonly id: UUID, readonly testField: Array<UUID>) {}
      }
  
      const result = GraphQLAdapter.getGraphQLTypeFor(TestReadModelWithArrayT)
      const expectedType = new GraphQLObjectType({
        name: 'TestReadModelWithArrayT',
        fields: {
          id: { type: GraphQLString },
          testField: { type: new GraphQLList(GraphQLString) },
        },
      })
      expect(result).to.deep.equal(expectedType)
    })
  })
})
