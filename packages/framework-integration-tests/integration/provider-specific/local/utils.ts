import fetch from 'cross-fetch'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import { LOCAL_PROVIDER_HOST } from './constants'
import gql from 'graphql-tag'
import { runCommand } from '../../helper/run-command'
import path = require('path')
import { ChildProcess } from 'child_process'

const cliBinaryPath = path.join('..', 'cli', 'bin', 'run')

export function start(environmentName = 'local', path: string): ChildProcess {
  return runCommand(path, `../${cliBinaryPath} start -e ${environmentName}`).childProcess
}

// --- Auth helpers ---

export async function createUser(username: string, password: string, role = ''): Promise<void> {
  const response = await fetch(signUpURL(), {
    method: 'POST',
    body: JSON.stringify({
      username: username,
      password: password,
      userAttributes: {
        role: role,
      },
    }),
    headers: {
      'Content-Type': 'application/json',
    },
  })

  await response.json()

  if (response.status != 200) {
    throw new Error('Failed to create a new user')
  }
}

export async function confirmUser(username: string): Promise<void> {
  const response: Response = await fetch(confirmUserURL(username), {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  })

  await response.json()

  if (response.status != 200) {
    throw new Error('Failed to confirm user')
  }
}

// --- URL helpers ---

export function signUpURL(): string {
  return new URL('auth/sign-up', LOCAL_PROVIDER_HOST).href
}

export function confirmUserURL(username: string): string {
  return new URL(`auth/confirm/${username}`, LOCAL_PROVIDER_HOST).href
}

export function signInURL(): string {
  return new URL('auth/sign-in', LOCAL_PROVIDER_HOST).href
}

export function signOutURL(): string {
  return new URL('auth/sign-out', LOCAL_PROVIDER_HOST).href
}

// --- GraphQL helpers ---

export async function graphQLClient(authToken?: string): Promise<ApolloClient<NormalizedCacheObject>> {
  const cache = new InMemoryCache()
  const headers = authToken ? { Authorization: `Bearer ${authToken}` } : {}
  const link = new HttpLink({
    uri: new URL('graphql', LOCAL_PROVIDER_HOST).href,
    headers,
    fetch,
  })

  return new ApolloClient({
    cache: cache,
    link: link,
    defaultOptions: {
      query: {
        fetchPolicy: 'no-cache',
      },
    },
  })
}

export async function changeCartItem(
  client: ApolloClient<NormalizedCacheObject>,
  cartId: string,
  productId: string,
  quantity: number
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<any> {
  return client.mutate({
    variables: {
      cartId: cartId,
      productId: productId,
      quantity: quantity,
    },
    mutation: gql`
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
    `,
  })
}
