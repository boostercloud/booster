import { GraphQLHelper } from './graphql-helper'
import { ProviderTestHelper } from './provider-test-helper'
import { TokenHelper } from './token-helper'

export class ApplicationTester {
  readonly graphql: GraphQLHelper
  readonly token: TokenHelper
  constructor(providerTestHelper: ProviderTestHelper) {
    this.graphql = new GraphQLHelper(providerTestHelper)
    this.token = new TokenHelper()
  }
}
