import { expect } from 'chai'
import { GraphQLAdapter } from '../../../src/library/graphql-adapter'
import { ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { GraphQLList, GraphQLString } from 'graphql'

class TestReadModel implements ReadModelInterface {
  public constructor(readonly id: UUID, readonly testField: UUID[]) {}
}

describe('GraphQLAdapter', () => {
  describe('getGraphQLTypeFor', () => {
    it('handles read models with T[] notation correctly', () => {
      const result = GraphQLAdapter.getGraphQLTypeFor(TestReadModel)
      expect(result).to.be.instanceOf(GraphQLList)
      expect(result.ofType).to.equal(GraphQLString)
    })
  })
})
