import { CloudFormation, CognitoIdentityServiceProvider, DynamoDB } from 'aws-sdk'
import { Stack, StackResourceDetail } from 'aws-sdk/clients/cloudformation'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ScanOutput = DocumentClient.ScanOutput
import QueryOutput = DocumentClient.QueryOutput
import { internet } from 'faker'
import { sleep } from '../helpers'
import { WebSocketLink } from 'apollo-link-ws'
import { getMainDefinition } from 'apollo-utilities'
import { split } from 'apollo-link'
import * as WebSocket from 'ws'
import { OperationOptions, SubscriptionClient } from 'subscriptions-transport-ws'
import { ApolloClientOptions } from 'apollo-client/ApolloClient'

const userPoolId = 'userpool'
const cloudFormation = new CloudFormation()
const cognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
const documentClient = new DynamoDB.DocumentClient()

// --- Stack Helpers ---

function appStackName(): string {
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
    throw `No stack found with name "${appStackName}"`
  }
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

// --- Auth helpers ---

export async function userPool(): Promise<StackResourceDetail> {
  const stackName = appStackName()

  const userPoolDescription = await cloudFormation
    .describeStackResource({
      LogicalResourceId: userPoolId,
      StackName: stackName,
    })
    .promise()

  if (userPoolDescription?.StackResourceDetail) {
    return userPoolDescription.StackResourceDetail
  } else {
    throw `No user pool details found in stack ${stackName} with resource Id ${userPoolId}`
  }
}

export async function userPoolPhysicalResourceId(): Promise<string> {
  const { PhysicalResourceId } = await userPool()
  if (PhysicalResourceId) {
    return PhysicalResourceId
  } else {
    throw 'Unable to get the PhisicalResourceId'
  }
}

export async function createUser(username: string, password: string, role = 'User'): Promise<void> {
  const physicalResourceId = await userPoolPhysicalResourceId()
  const clientId = await authClientID()
  const temporaryPassword = 'ChangeMePleas3!'

  await cognitoIdentityServiceProvider
    .adminCreateUser({
      UserPoolId: physicalResourceId,
      Username: username,
      TemporaryPassword: temporaryPassword,
      MessageAction: 'SUPPRESS',
      UserAttributes: [
        {
          Name: 'email_verified',
          Value: 'True',
        },
        {
          Name: 'email',
          Value: username,
        },
      ],
    })
    .promise()

  // Setting the roles user attribute on creation triggers an error in the PreSignUp lambda, so we have to set them in a separate call
  // > UserLambdaValidationException: PreSignUp failed with error User with role Admin can't sign up by themselves. Choose a different role or contact and administrator.
  // TODO: Should this be the expected behavior taking into account that we're creating it via admin API?
  // We might want to consider changing the pre-sign-up lambda to check for some kind of credentials to create admin users or something like that.
  await cognitoIdentityServiceProvider
    .adminUpdateUserAttributes({
      UserPoolId: physicalResourceId,
      Username: username,
      UserAttributes: [
        {
          Name: 'custom:roles',
          Value: role,
        },
      ],
    })
    .promise()

  // A definitive password can't be set on creation, the user is created with a temporal password
  // here we're simulating a user resetting their password.
  const authTrialDetails = await cognitoIdentityServiceProvider
    .initiateAuth({
      AuthFlow: 'USER_PASSWORD_AUTH',
      ClientId: clientId,
      AuthParameters: {
        USERNAME: username,
        PASSWORD: temporaryPassword,
      },
    })
    .promise()

  await cognitoIdentityServiceProvider
    .respondToAuthChallenge({
      ChallengeName: authTrialDetails.ChallengeName ?? 'NEW_PASSWORD_REQUIRED',
      ClientId: clientId,
      ChallengeResponses: {
        USERNAME: username,
        NEW_PASSWORD: password,
      },
      Session: authTrialDetails.Session,
    })
    .promise()
}

export async function confirmUser(username: string): Promise<void> {
  const physicalResourceId = await userPoolPhysicalResourceId()
  await cognitoIdentityServiceProvider
    .adminConfirmSignUp({
      UserPoolId: physicalResourceId,
      Username: username,
    })
    .promise()
}

export async function deleteUser(username: string): Promise<void> {
  const phisicalResouceId = await userPoolPhysicalResourceId()
  await cognitoIdentityServiceProvider
    .adminDeleteUser({
      UserPoolId: phisicalResouceId,
      Username: username,
    })
    .promise()
}

export const getAuthToken = async (email: string, password: string): Promise<string> => {
  const url = await signInURL()
  const clientId = await authClientID()

  const response = await fetch(url, {
    method: 'POST',
    body: JSON.stringify({
      clientId: clientId,
      username: email,
      password: password,
    }),
    headers: {
      'Content-Type': 'application/json',
    },
  })

  return (await response.json()).accessToken
}

export const createPassword = (): string => {
  return `${internet.password(8)}Passw0rd!`
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

export async function signUpURL(): Promise<string> {
  return new URL('auth/sign-up', await baseHTTPURL()).href
}

export async function signInURL(): Promise<string> {
  return new URL('auth/sign-in', await baseHTTPURL()).href
}

// --- GraphQL helpers ---

export async function graphQLClient(authToken?: string): Promise<ApolloClient<NormalizedCacheObject>> {
  return new ApolloClient({
    cache: new InMemoryCache(),
    link: await getApolloHTTPLink(authToken),
    defaultOptions: {
      query: {
        fetchPolicy: 'no-cache',
      },
    },
  })
}

async function getApolloHTTPLink(authToken?: string): Promise<HttpLink> {
  const httpURL = await baseHTTPURL()
  const headers: Record<string, string> = authToken ? { Authorization: `Bearer ${authToken}` } : {}
  return new HttpLink({
    uri: new URL('graphql', httpURL).href,
    headers,
    fetch,
  })
}

export class DisconnectableApolloClient extends ApolloClient<NormalizedCacheObject> {
  constructor(
    private readonly subscriptionClient: SubscriptionClient,
    options: ApolloClientOptions<NormalizedCacheObject>
  ) {
    super(options)
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
export async function graphQLClientWithSubscriptions(authToken?: string): Promise<DisconnectableApolloClient> {
  const subscriptionClient: SubscriptionClient = await graphqlSubscriptionsClient()
  if (authToken) {
    subscriptionClient.use([
      {
        applyMiddleware(options: OperationOptions, next: Function): void {
          options.Authorization = authToken
          next()
        },
      },
    ])
  }

  const websocketLink = new WebSocketLink(subscriptionClient)

  const link = split(
    ({ query }) => {
      const definition = getMainDefinition(query)
      return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
    },
    websocketLink,
    await getApolloHTTPLink(authToken)
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

export async function graphqlSubscriptionsClient(authToken?: string): Promise<SubscriptionClient> {
  const headers: Record<string, string> = authToken ? { Authorization: `Bearer ${authToken}` } : {}
  return new SubscriptionClient(
    await baseWebsocketURL(),
    {
      lazy: true,
      reconnect: true,
    },
    class MyWebSocket extends WebSocket {
      public constructor(url: string, protocols?: string | string[]) {
        super(url, protocols, {
          headers,
        })
        this.addListener('open', (): void => {
          console.log('[GraphQL socket] on open')
        })
        this.addListener('ping', (): void => {
          console.log('[GraphQL socket] on "ping"')
        })
        this.addListener('pong', (): void => {
          console.log('[GraphQL socket] on "pong"')
        })
        this.addListener('message', (data: WebSocket.Data): void => {
          console.log('[GraphQL socket] on message: ', data)
        })
        this.addListener('close', (code: number, message: string): void => {
          console.log('[GraphQL socket] on close: ', code, message)
        })
        this.addListener('error', (err: Error): void => {
          console.log('[GraphQL socket] on error: ', err.message)
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
      KeyConditionExpression: 'entityTypeName_entityID_kind = :v',
      ExpressionAttributeValues: { ':v': primaryKey },
      ScanIndexForward: !latestFirst,
    })
    .promise()

  return output.Items
}

// --- Subscriptions store helpers ---
export async function subscriptionsTableName(): Promise<string> {
  const stackName = appStackName()

  return `${stackName}-subscriptions-store`
}

export async function countSubscriptionsItems(): Promise<number> {
  const tableName = await subscriptionsTableName()

  return countTableItems(tableName)
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

export async function countSnapshotItems(): Promise<number> {
  const output: ScanOutput = await documentClient
    .scan({
      TableName: await eventsStoreTableName(),
      Select: 'COUNT',
      FilterExpression: '#k = :kind',
      ExpressionAttributeNames: { '#k': 'kind' },
      ExpressionAttributeValues: { ':kind': 'snapshot' },
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

export async function clearSubscriptions(): Promise<any> {
  await documentClient.scan({
    TableName: await subscriptionsTableName(),
    Select: 'ALL_ATTRIBUTES',
  })
}

// --- Other helpers ---

export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean,
  tryEveryMs = 1000,
  timeoutMs = 60000
): Promise<TResult> {
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    console.debug('[waitForIt] Executing function')
    const res = await tryFunction()
    console.debug('[waitForIt] Checking result')
    const expectedResult = checkResult(res)
    if (expectedResult) {
      console.debug('[waitForIt] Result is expected. Wait finished.')
      return res
    }
    console.debug('[waitForIt] Result is not expected. Keep trying...')
    const elapsed = Date.now() - start
    console.debug('[waitForIt] Time elapsed (ms): ' + elapsed)

    if (elapsed > timeoutMs) {
      throw new Error('[waitForIt] Timeout reached waiting for a successful execution')
    }

    const nextExecutionDelay = (timeoutMs - elapsed) % tryEveryMs
    console.debug('[waitForIt] Trying again in ' + nextExecutionDelay)
    await sleep(nextExecutionDelay)
    return doWaitFor()
  }
}
