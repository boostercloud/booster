import { ApolloClient, ApolloLink, HttpLink, InMemoryCache, NormalizedCacheObject, split } from '@apollo/client'
import { WebSocketLink } from '@apollo/client/link/ws'
import { getMainDefinition } from '@apollo/client/utilities'
import { SubscriptionClient } from 'subscriptions-transport-ws'

export class ApolloService {
  static initClient(httpUri: string, wsUri: string): ApolloClient<NormalizedCacheObject> {
    const httpLink = new HttpLink({
      uri: httpUri,
    })

    const wsLink = new WebSocketLink(
      new SubscriptionClient(wsUri, {
        reconnect: true,
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
