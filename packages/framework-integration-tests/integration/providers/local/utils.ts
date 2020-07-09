import fetch from 'cross-fetch'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import { LOCAL_PROVIDER_HOST } from './constants'

// --- Auth helpers ---

export async function createUser(username: string, password: string, roles: string[] = []): Promise<void> {
  const response = await fetch(signUpURL(), {
    method: 'POST',
    body: JSON.stringify({
      username: username,
      password: password,
      userAttributes: {
        roles: roles,
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
