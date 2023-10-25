import { ApolloClient, ApolloLink, HttpLink, InMemoryCache, NormalizedCacheObject, split } from '@apollo/client'
import { WebSocketLink } from '@apollo/client/link/ws'
import fetch from 'cross-fetch'
import { ProviderTestHelper } from './provider-test-helper'
import { getMainDefinition } from '@apollo/client/utilities'

type AuthToken = string | (() => string)

export class GraphQLHelper {
  constructor(private providerTestHelper: ProviderTestHelper) {}

  public client(authToken?: AuthToken): ApolloClient<NormalizedCacheObject> {
    return new ApolloClient({
      cache: new InMemoryCache(),
      link: this.getAuthLink(authToken).concat(this.getApolloHTTPLink()),
      defaultOptions: {
        query: {
          fetchPolicy: 'no-cache',
        },
      },
    })
  }

  public async clientWithSubscriptions(authToken?: AuthToken): Promise<ApolloClient<NormalizedCacheObject>> {
    const wsLink = new WebSocketLink({
      uri: this.providerTestHelper.outputs.websocketURL,
      options: {
        reconnect: true,
        connectionParams: () => {
          if (authToken) {
            const token = typeof authToken == 'function' ? authToken() : authToken
            return { Authorization: 'Bearer ' + token }
          }
          return {}
        },
      },
    })

    const link = split(
      ({ query }) => {
        const definition = getMainDefinition(query)
        return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
      },
      wsLink,
      this.getAuthLink(authToken).concat(this.getApolloHTTPLink())
    )

    return new ApolloClient({
      cache: new InMemoryCache(),
      link: link,
      defaultOptions: {
        query: {
          fetchPolicy: 'no-cache',
        },
      },
    })
  }

  private getApolloHTTPLink(): HttpLink {
    return new HttpLink({
      uri: this.providerTestHelper.outputs.graphqlURL,
      fetch,
    })
  }

  private getAuthLink(authToken?: AuthToken): ApolloLink {
    return new ApolloLink((operation, forward) => {
      if (authToken) {
        const token = typeof authToken == 'function' ? authToken() : authToken
        operation.setContext({ headers: { Authorization: 'Bearer ' + token } })
      }
      return forward(operation)
    })
  }
}
