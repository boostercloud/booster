import {
  ApolloClient,
  ApolloClientOptions,
  ApolloLink,
  HttpLink,
  InMemoryCache,
  NormalizedCacheObject,
  split,
} from '@apollo/client'
import { WebSocketLink } from '@apollo/client/link/ws'
import fetch from 'cross-fetch'
import * as WebSocket from 'ws'
import { SubscriptionClient } from 'subscriptions-transport-ws'
import { ProviderTestHelper } from './provider-test-helper'
import { getMainDefinition } from '@apollo/client/utilities'

type AuthToken = string | (() => string)

export class GraphQLHelper {
  constructor(private providerTestHelper: ProviderTestHelper) {}

  public client(authToken?: AuthToken): ApolloClient<NormalizedCacheObject> {
    return new ApolloClient({
      cache: new InMemoryCache(),
      link: this.getAuthLink(undefined, authToken).concat(this.getApolloHTTPLink()),
      defaultOptions: {
        query: {
          fetchPolicy: 'no-cache',
        },
      },
    })
  }

  /**
   * IMPORTANT: After using this "DisconnectableApolloClient", you must call ".disconnect()" to close the socket. Otherwise
   * it will keep waiting for messages forever
   */
  public async clientWithSubscriptions(authToken?: AuthToken): Promise<DisconnectableApolloClient> {
    const subscriptionClient: SubscriptionClient = await this.subscriptionsClient(authToken)
    console.log(this.providerTestHelper.outputs.graphqlURL)
    console.log(this.providerTestHelper.outputs.websocketURL)

    const link = split(
      ({ query }) => {
        const definition = getMainDefinition(query)
        return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
      },
      this.getAuthLink(subscriptionClient, authToken),
      this.getAuthLink(undefined, authToken).concat(this.getApolloHTTPLink())
    )

    return new DisconnectableApolloClient(subscriptionClient, {
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

  private getAuthLink(subscriptionClient?: SubscriptionClient, authToken?: string | (() => string)): ApolloLink {
    if (subscriptionClient) return new WebSocketLink(subscriptionClient)

    return new ApolloLink((operation, forward) => {
      if (authToken) {
        const token = typeof authToken == 'function' ? authToken() : authToken
        operation.setContext({ headers: { Authorization: 'Bearer ' + token } })
      }
      return forward(operation)
    })
  }

  private async subscriptionsClient(authToken?: AuthToken): Promise<SubscriptionClient> {
    return new Promise((resolve, reject) => {
      const subscriptionClient = new SubscriptionClient(
        this.providerTestHelper.outputs.websocketURL,
        {
          reconnect: true,
          connectionParams: () => {
            if (authToken) {
              const token = typeof authToken == 'function' ? authToken() : authToken
              return {
                Authorization: 'Bearer ' + token,
              }
            }
            return {}
          },
          connectionCallback: (err?: any) => {
            if (err) {
              reject(err)
              return
            }
            resolve(subscriptionClient)
          },
        },
        class MyWebSocket extends WebSocket {
          public constructor(url: string, protocols?: string | string[]) {
            super(url, protocols)

            this.addListener('open', (): void => {
              console.debug('[GraphQL socket] on open')
            })
            this.addListener('ping', (): void => {
              console.debug('[GraphQL socket] on "ping"')
            })
            this.addListener('pong', (): void => {
              console.debug('[GraphQL socket] on "pong"')
            })
            this.addListener('message', (data: WebSocket.Data): void => {
              console.debug('[GraphQL socket] on message: ', data)
            })
            this.addListener('close', (code: number, message: string): void => {
              console.debug('[GraphQL socket] on close: ', code, message)
            })
            this.addListener('error', (err: Error): void => {
              console.debug('[GraphQL socket] on error: ', err.message)
            })
          }
        }
      )
    })
  }
}

export class DisconnectableApolloClient extends ApolloClient<NormalizedCacheObject> {
  constructor(
    private readonly subscriptionClient: SubscriptionClient,
    options: ApolloClientOptions<NormalizedCacheObject>
  ) {
    super(options)
  }

  public reconnect(): Promise<void> {
    const reconnectPromise = new Promise<void>((resolve) => {
      this.subscriptionClient.onReconnected(resolve)
    })
    this.subscriptionClient.close(false)
    return reconnectPromise
  }

  public disconnect(): void {
    this.subscriptionClient.close()
    this.stop()
  }
}
