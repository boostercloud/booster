import { GraphQLHelper } from './graphql-helper'
import { Counters, ProviderTestHelper, Queries } from './provider-test-helper'
import { TokenHelper } from './token-helper'
import { HttpHelper } from './http-helper'

export class ApplicationTester {
  readonly graphql: GraphQLHelper
  readonly http: HttpHelper
  readonly token: TokenHelper
  readonly count: Counters
  readonly query: Queries
  constructor(providerTestHelper: ProviderTestHelper) {
    this.graphql = new GraphQLHelper(providerTestHelper)
    this.http = new HttpHelper(providerTestHelper)
    this.token = new TokenHelper()
    this.count = providerTestHelper.counters
    this.query = providerTestHelper.queries
  }
}
