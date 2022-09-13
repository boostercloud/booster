import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'
import { WebSocketLink } from 'apollo-link-ws'
import { getMainDefinition } from 'apollo-utilities'
import { ApolloLink, split } from 'apollo-link'
import * as WebSocket from 'ws'
import { SubscriptionClient } from 'subscriptions-transport-ws'
import { ApolloClientOptions } from 'apollo-client/ApolloClient'
import { ProviderTestHelper } from './provider-test-helper'

type AuthToken = string | (() => string)

export class GraphQLHelper {
  constructor(private providerTestHelper: ProviderTestHelper) {}

  public client(authToken?: AuthToken): ApolloClient<NormalizedCacheObject> {
    return new ApolloClient({
      cache: new InMemoryCache(),
      link: this.buildLink(authToken),
      defaultOptions: {
        query: {
          fetchPolicy: 'no-cache',
        },
      },
    })
  }

  private buildLink(authToken?: AuthToken): ApolloLink | undefined {
    // ESLint thinks this is an array, but its not, disabling rule
    // eslint-disable-next-line unicorn/prefer-spread
    return this.getAuthLink(authToken).concat(this.getApolloHTTPLink())
  }

  /**
   * IMPORTANT: After using this "DisconnectableApolloClient", you must call ".disconnect()" to close the socket. Otherwise
   * it will keep waiting for messages forever
   */
  public async clientWithSubscriptions(authToken?: AuthToken): Promise<DisconnectableApolloClient> {
    const subscriptionClient: SubscriptionClient = await this.subscriptionsClient(authToken)

    const link = split(
      ({ query }) => {
        const definition = getMainDefinition(query)
        return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
      },
      new WebSocketLink(subscriptionClient),
      this.buildLink(authToken)
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

  private getAuthLink(authToken?: string | (() => string)): ApolloLink {
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
          connectionCallback: (error?: any) => {
            if (error) {
              reject(error)
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
              console.debug('[GraphQL socket] on message:', data)
            })
            this.addListener('close', (code: number, message: string): void => {
              console.debug('[GraphQL socket] on close:', code, message)
            })
            this.addListener('error', (error: Error): void => {
              console.debug('[GraphQL socket] on error:', error.message)
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
