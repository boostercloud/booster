import { CloudFormation, CognitoIdentityServiceProvider, DynamoDB } from 'aws-sdk'
import { Stack, StackResourceDetail } from 'aws-sdk/clients/cloudformation'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ScanOutput = DocumentClient.ScanOutput
import QueryOutput = DocumentClient.QueryOutput
import { sleep } from '../helpers'

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

export async function createAdmin(username: string, password: string): Promise<void> {
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
          Value: 'Admin',
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

// --- URL helpers ---

export async function baseURL(): Promise<string> {
  const { Outputs } = await appStack()
  const url = Outputs?.find((output) => {
    return output.OutputKey === 'httpURL'
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

export async function signInURL(): Promise<string> {
  return new URL('auth/sign-in', await baseURL()).href
}

// --- GraphQL helpers ---

export async function graphQLClient(authToken?: string): Promise<ApolloClient<NormalizedCacheObject>> {
  const url = await baseURL()
  const cache = new InMemoryCache()
  const headers = authToken ? { Authorization: `Bearer ${authToken}` } : {}
  const link = new HttpLink({
    uri: new URL('graphql', url).href,
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

// --- DynamoDB helpers ---

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
