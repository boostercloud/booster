import { ApolloClient, ApolloLink, HttpLink, InMemoryCache, NormalizedCacheObject, split } from '@apollo/client'
import { GraphQLWsLink } from '@apollo/client/link/subscriptions'
import { getMainDefinition } from '@apollo/client/utilities'
import { createClient } from 'graphql-ws'

export class ApolloService {
  static initClient(httpUri: string, wsUri: string): ApolloClient<NormalizedCacheObject> {
    const httpLink = new HttpLink({
      uri: httpUri,
    })

    const wsLink = new GraphQLWsLink(
      createClient({
        url: wsUri,
      })
    )

    const splitLink = split(
      ({ query }) => {
        const definition = getMainDefinition(query)
        return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
      },
      wsLink,
      httpLink
    )

    return new ApolloClient({
      cache: new InMemoryCache(),
      link: ApolloLink.from([splitLink]),
    })
  }
}
