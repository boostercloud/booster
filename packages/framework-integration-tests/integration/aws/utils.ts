import { CloudFormation } from 'aws-sdk'
import { Stack } from 'aws-sdk/clients/cloudformation'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'

function appStackName(): string {
  return `my-store-${process.env.BOOSTER_APP_SUFFIX}-application-stack`
}

export async function appStack(): Promise<Stack> {
  const cloudformation = new CloudFormation()
  const { Stacks } = await cloudformation
    .describeStacks({
      StackName: appStackName(),
    })
    .promise()

  if (Stacks?.[0]) {
    return Stacks[0]
  } else {
    throw `No stack found with name "${appStackName}"`
  }
}

export async function baseURL(): Promise<string> {
  const { Outputs } = await appStack()
  const url = Outputs?.find((output) => {
    return output.OutputKey === 'baseRESTURL'
  })?.OutputValue

  if (url) {
    return url
  } else {
    throw 'Unable to get the Base REST URL from the current stack'
  }
}

export async function signUpURL(): Promise<string> {
  return new URL('auth/sign-up', await baseURL()).href
}

export async function graphQLClient(): Promise<ApolloClient<NormalizedCacheObject>> {
  const url = await baseURL()
  const cache = new InMemoryCache()
  const link = new HttpLink({
    uri: new URL('graphql', url).href,
    fetch,
  })

  return new ApolloClient({
    // Provide required constructor fields
    cache: cache,
    link: link,

    // Provide some optional constructor fields
    name: 'react-web-client',
    version: '1.3',
    queryDeduplication: false,
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
      },
    },
  })
}

export async function authClientID(): Promise<string> {
  const { Outputs } = await appStack()
  const clientId = Outputs?.find((output) => {
    return output.OutputKey === 'clientID'
  })?.OutputValue

  if (clientId) {
    return clientId
  } else {
    throw 'unable to find the clientID from the current stack'
  }
}
