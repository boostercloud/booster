import { CloudFormation, CognitoIdentityServiceProvider } from 'aws-sdk'
import { Stack, StackResourceDetail } from 'aws-sdk/clients/cloudformation'
import { ApolloClient } from 'apollo-client'
import { InMemoryCache, NormalizedCacheObject } from 'apollo-cache-inmemory'
import { HttpLink } from 'apollo-link-http'
import fetch from 'cross-fetch'

const userPoolId = 'userpool'
const cloudFormation = new CloudFormation()
const cognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()

// --- Stack Helpers ---

function appStackName(): string {
  return `my-store-${process.env.BOOSTER_APP_SUFFIX}-application-stack`
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

// --- URL helpers ---

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

export async function signInURL(): Promise<string> {
  return new URL('auth/sign-in', await baseURL()).href
}

// --- GraphQL helpers ---

export async function graphQLClient(): Promise<ApolloClient<NormalizedCacheObject>> {
  const url = await baseURL()
  const cache = new InMemoryCache()
  const link = new HttpLink({
    uri: new URL('graphql', url).href,
    fetch,
  })

  return new ApolloClient({
    cache: cache,
    link: link,
  })
}
