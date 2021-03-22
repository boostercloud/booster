import { CloudFormation, config, DynamoDB } from 'aws-sdk'
import { Stack, StackResourceSummary } from 'aws-sdk/clients/cloudformation'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import { WebSocketLink } from 'apollo-link-ws'
import { getMainDefinition } from 'apollo-utilities'
import { ApolloLink, split } from 'apollo-link'
import * as WebSocket from 'ws'
import { SubscriptionClient } from 'subscriptions-transport-ws'
import { ApolloClientOptions } from 'apollo-client/ApolloClient'
import * as jwt from 'jsonwebtoken'
import * as fs from 'fs'
import util = require('util')
import QueryOutput = DocumentClient.QueryOutput
import ScanOutput = DocumentClient.ScanOutput
import { sleep } from '../../helper/sleep'

const exec = util.promisify(require('child_process').exec)

const cloudFormation = new CloudFormation()
const documentClient = new DynamoDB.DocumentClient()

// Environment helpers
export async function setEnv(): Promise<void> {
  if (!process.env.BOOSTER_APP_SUFFIX) {
    // If the user doesn't set an app name suffix, use the current git commit hash
    // to build a unique suffix for the application name in AWS to avoid collisions
    // between tests from different branches.
    const { stdout } = await exec('git rev-parse HEAD')
    process.env['BOOSTER_APP_SUFFIX'] = stdout.trim().substring(0, 6)
    console.log('setting BOOSTER_APP_SUFFIX=' + process.env.BOOSTER_APP_SUFFIX)
  }
  // The following variable is set to make AWS SDK try to load the region config from
  // `~/.aws/config` if it fails reading it from `/.aws/credentials`. Loading the region doesn't seem
  // to be a very reliable process, so in some cases we'll need to set the environment variable
  // AWS_REGION to our chosen region to make this thing work...
  process.env['AWS_SDK_LOAD_CONFIG'] = 'true'
  console.log('setting AWS_SDK_LOAD_CONFIG=' + process.env.AWS_SDK_LOAD_CONFIG)
}

export async function checkConfigAnd(action: () => Promise<void>): Promise<void> {
  console.log('Checking AWS configuration...')
  if (!config.credentials?.accessKeyId || !config.credentials?.secretAccessKey) {
    throw new Error(
      "AWS credentials were not properly loaded by the AWS SDK and are required to run the integration tests. Check that you've set them in your `~/.aws/credentials` file or environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html"
    )
  }
  if (!config.region) {
    throw new Error(
      "AWS region was not properly loaded by the AWS SDK and is required to run the integration tests. Check that you've set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence"
    )
  } else {
    console.log('AWS Region set to ' + config.region)
  }
  await action()
}

export async function hopeTheBest(): Promise<void> {
  const stack = await appStack() // This function already throws when the stack is not found
  const validStatuses = ['CREATE_COMPLETE', 'UPDATE_COMPLETE']
  if (!validStatuses.includes(stack?.StackStatus)) {
    throw new Error(`The stack '${appStackName()}' status is ${stack?.StackStatus} and tests can't run.`)
  }
}

// --- Stack Helpers ---

export function appStackName(): string {
  return `my-store-${process.env.BOOSTER_APP_SUFFIX}-app`
}

export async function appStack(): Promise<Stack> {
  const { Stacks } = await cloudFormation
    .describeStacks({
      StackName: appStackName(),
    })
    .promise()

  if (Stacks?.[0]) {
    return Stacks[0]
  } else {
    throw `No stack found with name "${appStackName}". Try running 'integration/aws-deploy' first to make sure that the AWS environment is properly set.`
  }
}

export async function stackResourcesByType(resourceType: string): Promise<Array<StackResourceSummary>> {
  const resources = await cloudFormation
    .listStackResources({
      StackName: appStackName(),
    })
    .promise()
  return resources.StackResourceSummaries?.filter((resource) => resource.ResourceType == resourceType) ?? []
}

// --- URL helpers ---

export async function baseHTTPURL(): Promise<string> {
  const { Outputs } = await appStack()
  const url = Outputs?.find((output) => {
    return output.OutputKey === 'httpURL'
  })?.OutputValue

  if (!url) {
    throw 'Unable to get the Base HTTP URL from the current stack'
  }
  return url
}

export async function baseWebsocketURL(): Promise<string> {
  const { Outputs } = await appStack()
  const url = Outputs?.find((output) => {
    return output.OutputKey === 'websocketURL'
  })?.OutputValue

  if (!url) {
    throw 'Unable to get the Base Websocket URL from the current stack'
  }
  return url
}

// --- GraphQL helpers ---

type AuthToken = string | (() => string)

export async function graphQLClient(authToken?: AuthToken): Promise<ApolloClient<NormalizedCacheObject>> {
  return new ApolloClient({
    cache: new InMemoryCache(),
    link: getAuthLink(authToken).concat(await getApolloHTTPLink()),
    defaultOptions: {
      query: {
        fetchPolicy: 'no-cache',
      },
    },
  })
}

async function getApolloHTTPLink(): Promise<HttpLink> {
  const httpURL = await baseHTTPURL()
  return new HttpLink({
    uri: new URL('graphql', httpURL).href,
    fetch,
  })
}

function getAuthLink(authToken?: string | (() => string)): ApolloLink {
  return new ApolloLink((operation, forward) => {
    if (authToken) {
      const token = typeof authToken == 'function' ? authToken() : authToken
      operation.setContext({ headers: { Authorization: 'Bearer ' + token } })
    }
    return forward(operation)
  })
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

/**
 * IMPORTANT: After using this "DisconnectableApolloClient", you must call ".disconnect()" to close the socket. Otherwise
 * it will keep waiting for messages forever
 * @param authToken
 * @param tokenInHeader
 */
export async function graphQLClientWithSubscriptions(
  authToken?: AuthToken,
  onConnected?: (err?: string) => void
): Promise<DisconnectableApolloClient> {
  const subscriptionClient: SubscriptionClient = await graphqlSubscriptionsClient(authToken, onConnected)

  const link = split(
    ({ query }) => {
      const definition = getMainDefinition(query)
      return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
    },
    new WebSocketLink(subscriptionClient),
    getAuthLink(authToken).concat(await getApolloHTTPLink())
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

export async function graphqlSubscriptionsClient(
  authToken?: AuthToken,
  onConnected?: (err?: string) => void
): Promise<SubscriptionClient> {
  return new SubscriptionClient(
    await baseWebsocketURL(),
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
        if (onConnected) {
          const errMessage = err ?? err.toString()
          onConnected(errMessage)
        }
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
}

// --- Events store helpers ---

export async function eventsStoreTableName(): Promise<string> {
  const stackName = appStackName()

  return `${stackName}-events-store`
}

export async function queryEvents(primaryKey: string, latestFirst = true): Promise<any> {
  const output: QueryOutput = await documentClient
    .query({
      TableName: await eventsStoreTableName(),
      ConsistentRead: true,
      KeyConditionExpression: 'entityTypeName_entityID_kind = :v',
      ExpressionAttributeValues: { ':v': primaryKey },
      ScanIndexForward: !latestFirst,
    })
    .promise()

  return output.Items
}

// --- Subscriptions store helpers ---
export async function countSubscriptionsItems(): Promise<number> {
  return countTableItems(`${appStackName()}-subscriptions-store`)
}

export async function countConnectionsItems(): Promise<number> {
  return countTableItems(`${appStackName()}-connections-store`)
}

// --- Read models helpers ---

export async function readModelTableName(readModelName: string): Promise<string> {
  const stackName = appStackName()

  return `${stackName}-${readModelName}`
}

export async function queryReadModels(primaryKey: string, readModelName: string, latestFirst = true): Promise<any> {
  const output: QueryOutput = await documentClient
    .query({
      TableName: await readModelTableName(readModelName),
      KeyConditionExpression: 'id = :v',
      ExpressionAttributeValues: { ':v': primaryKey },
      ScanIndexForward: !latestFirst,
    })
    .promise()

  return output.Items
}

export async function countReadModelItems(readModelName: string): Promise<number> {
  const tableName = await readModelTableName(readModelName)

  return countTableItems(tableName)
}

// --- DynamoDB helpers ---

export async function countTableItems(tableName: string): Promise<number> {
  const output: ScanOutput = await documentClient
    .scan({
      TableName: tableName,
      Select: 'COUNT',
    })
    .promise()

  return output.Count ?? -1
}

export async function countEventItems(): Promise<number> {
  // TODO: This way of counting is wrong when there are a lot of events in the event store, as COUNT will only contain
  // the number of elements in a a result set that is lower than 1MB in size:
  // "If the size of the Query result set is larger than 1 MB, then ScannedCount and Count
  // will represent only a partial count of the total items.
  // You will need to perform multiple Query operations in order to retrieve all of the results\"."
  const output: ScanOutput = await documentClient
    .scan({
      TableName: await eventsStoreTableName(),
      Select: 'COUNT',
      FilterExpression: '#k = :kind',
      ExpressionAttributeNames: { '#k': 'kind' },
      ExpressionAttributeValues: { ':kind': 'event' },
    })
    .promise()

  return output.Count ?? -1
}

export async function countSnapshotItems(entityTypeName: string, entityID: string): Promise<number> {
  const output: QueryOutput = await documentClient
    .query({
      TableName: await eventsStoreTableName(),
      Select: 'COUNT',
      ConsistentRead: true,
      KeyConditionExpression: 'entityTypeName_entityID_kind = :partitionKey',
      ExpressionAttributeValues: {
        ':partitionKey': `${entityTypeName}-${entityID}-snapshot`,
      },
    })
    .promise()

  return output.Count ?? -1
}

export async function getEventsByEntityId(entityID: string): Promise<any> {
  const output: QueryOutput = await documentClient
    .scan({
      TableName: await eventsStoreTableName(),
      Select: 'ALL_ATTRIBUTES',
      ExpressionAttributeNames: { '#entityID': 'entityID' },
      ExpressionAttributeValues: { ':entityID': entityID },
      FilterExpression: '#entityID = :entityID',
    })
    .promise()

  return output.Items
}

// --- Other helpers ---

export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean,
  trialDelayMs = 1000,
  timeoutMs = 60000
): Promise<TResult> {
  console.debug('[waitForIt] start')
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    console.debug('.')
    const res = await tryFunction()
    if (checkResult(res)) {
      console.debug('[waitForIt] match!')
      return res
    }
    const elapsed = Date.now() - start

    if (elapsed > timeoutMs) {
      throw new Error('[waitForIt] Timeout reached')
    }

    await sleep(trialDelayMs)
    return doWaitFor()
  }
}

// This helper will create a valid token using a real private key for testing
// the tokens will be validate against the public keyset file
// located in: /keys/private.key file
export const getTokenForUser = (email: string, role: string): string => {
  const privateKey = fs.readFileSync(__dirname + '/keys/private.key')
  const keyid = 'booster'
  const issuer = 'booster'
  const token = jwt.sign(
    {
      id: email,
      'custom:role': role,
      email,
    },
    privateKey,
    {
      algorithm: 'RS256',
      subject: email,
      issuer,
      keyid,
    }
  )
  return token
}
