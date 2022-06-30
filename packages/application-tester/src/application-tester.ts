import { GraphQLHelper } from './graphql-helper'
import { Counters, ProviderTestHelper, Queries } from '@boostercloud/framework-types'
import { TokenHelper } from './token-helper'

export class ApplicationTester {
  readonly graphql: GraphQLHelper
  readonly token: TokenHelper
  readonly count: Counters
  readonly query: Queries
  constructor(providerTestHelper: ProviderTestHelper) {
    this.graphql = new GraphQLHelper(providerTestHelper)
    this.token = new TokenHelper()
    this.count = providerTestHelper.counters
    this.query = providerTestHelper.queries
  }
}
