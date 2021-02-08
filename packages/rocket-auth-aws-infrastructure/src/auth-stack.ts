import { CfnOutput, Stack } from '@aws-cdk/core'
import {
  StringAttribute,
  UserPool,
  UserPoolClient,
  VerificationEmailStyle,
  UserPoolDomain,
  UserPoolTriggers,
} from '@aws-cdk/aws-cognito'
import { Effect, PolicyStatement } from '@aws-cdk/aws-iam'
import { LambdaIntegration, Resource, RestApi, Cors, CorsOptions } from '@aws-cdk/aws-apigateway'
import { createLambda } from './utils'
import { BoosterConfig, JWT_ENV_VARS } from '@boostercloud/framework-types'
import { Function } from '@aws-cdk/aws-lambda'

export interface AWSAuthRocketParams {
  passwordPolicy?: {
    minLength?: number
    requireDigits: boolean
    requireLowercase: boolean
    requireSymbols: boolean
    requireUppercase: boolean
  }
  mode: 'Passwordless' | 'UserPassword'
}

type TokenVerifier = {
  issuer: string
  jwksUri: string
}

// Type to unify the signature of all private methods. It will be filled along the way
type ResourceParams = {
  params: AWSAuthRocketParams
  stack: Stack
  config: BoosterConfig
  rocketStackPrefixId: string
  rootResource?: Resource
  userPool?: UserPool
  userPoolClientId?: string
  defaultCorsPreflightOptions?: CorsOptions
  authApi?: RestApi
  tokenVerifier?: TokenVerifier
}

export class AuthStack {
  public static mountStack(params: AWSAuthRocketParams, stack: Stack, config: BoosterConfig): void {
    if (config.thereAreRoles) {
      const rocketStackPrefixId = AuthStack.rocketArtifactsPrefix(config)

      const resourceParams: ResourceParams = {
        params,
        stack,
        config,
        rocketStackPrefixId,
      }

      const { userPool, userPoolClient } = AuthStack.createUserPoolAndUserPoolClient(resourceParams)
      resourceParams.userPool = userPool
      resourceParams.userPoolClientId = userPoolClient.userPoolClientId

      const authApi = AuthStack.createAuthResources(resourceParams)
      resourceParams.authApi = authApi

      const tokenVerifier = AuthStack.tokenVerifier(resourceParams)
      resourceParams.tokenVerifier = tokenVerifier

      AuthStack.createEnvVars(resourceParams)
      AuthStack.printOutput(resourceParams)
    }
  }

  public static unmountStack?(): void {}

  public static rocketArtifactsPrefix(config: BoosterConfig): string {
    return `${config.appName}-${config.environmentName}-rocket-auth`
  }

  private static createUserPoolAndUserPoolClient(
    resourceParams: ResourceParams
  ): { userPool: UserPool; userPoolClient: UserPoolClient } {
    const { params, rocketStackPrefixId, config, stack } = resourceParams

    const userPoolID = `${rocketStackPrefixId}-user-pool`
    const userPassword = params.mode === 'UserPassword'
    const authChallenges = AuthStack.createAuthChallenges(resourceParams)
    const preSignUpTrigger = createLambda(
      stack,
      `${AuthStack.rocketArtifactsPrefix(config)}-pre-sign-up`,
      'pre-sign-up.handler',
      {
        rolesConfig: JSON.stringify(config.roles),
      }
    )

    const userPool = new UserPool(stack, userPoolID, {
      userPoolName: userPoolID,
      signInAliases: {
        email: true,
        phone: true,
      },
      autoVerify: {
        email: true,
        phone: true,
      },
      customAttributes: {
        role: new StringAttribute({ mutable: true }),
      },
      selfSignUpEnabled: true,
      passwordPolicy: userPassword
        ? params.passwordPolicy
        : { requireDigits: false, requireLowercase: false, requireUppercase: false, requireSymbols: false },
      userVerification: {
        emailStyle: VerificationEmailStyle.LINK,
      },
      lambdaTriggers: {
        ...authChallenges,
        preSignUp: preSignUpTrigger,
      },
    })

    const localUserPoolDomainID = `${rocketStackPrefixId}-user-pool-domain`
    new UserPoolDomain(stack, localUserPoolDomainID, {
      userPool,
      cognitoDomain: { domainPrefix: config.appName },
    })

    const userPoolClientID = `${rocketStackPrefixId}-user-pool-client`

    const userPoolClient = new UserPoolClient(stack, userPoolClientID, {
      userPoolClientName: userPoolClientID,
      userPool,
      authFlows: { userPassword, custom: !userPassword },
    })

    return { userPool, userPoolClient }
  }

  /**
   * It creates auth challenges only for OTP flow based. More info: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-challenge.html
   * The createAuthChallenge will send an SMS to the user using its phone number.
   * @param resourceParams: current resource params
   * @returns UserPoolTriggers: define, create and verify triggers)
   */
  public static createAuthChallenges(resourceParams: ResourceParams): UserPoolTriggers | undefined {
    const { rocketStackPrefixId, stack, params } = resourceParams

    if (params.mode === 'UserPassword') return

    const createAuthChallenge = createLambda(
      stack,
      `${rocketStackPrefixId}-create-auth-challenge`,
      'challenge-create.handler'
    )

    createAuthChallenge.addToRolePolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['sns:Publish'],
        resources: ['*'],
      })
    )

    const defineAuthChallenge = createLambda(
      stack,
      `${rocketStackPrefixId}-define-auth-challenge`,
      'challenge-define.handler'
    )

    const verifyAuthChallengeResponse = createLambda(
      stack,
      `${rocketStackPrefixId}-verify-auth-challenge`,
      'challenge-verify.handler'
    )

    return {
      createAuthChallenge,
      defineAuthChallenge,
      verifyAuthChallengeResponse,
    }
  }

  /**
   * It creates a REST API with all the endpoints needed to support auth flows
   * @param resourceParams current resource params
   * @returns RestApi
   */
  private static createAuthResources(resourceParams: ResourceParams): RestApi {
    const { rocketStackPrefixId, config, stack } = resourceParams

    const rootAuthAPI = new RestApi(stack, `${rocketStackPrefixId}-api`, {
      deployOptions: { stageName: config.environmentName },
    })

    const rootResource = rootAuthAPI.root.addResource('auth')

    const defaultCorsPreflightOptions: CorsOptions = {
      allowHeaders: ['*'],
      allowOrigins: Cors.ALL_ORIGINS,
      allowMethods: ['POST', 'OPTIONS'],
    }

    resourceParams.defaultCorsPreflightOptions = defaultCorsPreflightOptions
    resourceParams.rootResource = rootResource

    AuthStack.createSignInResources(resourceParams)
    AuthStack.createSignUpResources(resourceParams)
    AuthStack.createTokenResources(resourceParams)
    AuthStack.createPasswordResources(resourceParams)

    return rootAuthAPI
  }

  /**
   * It creates /sign-in endpoint
   * @param resourceParams current resource params
   * @returns void
   */
  private static createSignInResources(resourceParams: ResourceParams): void {
    const { rootResource, defaultCorsPreflightOptions } = resourceParams
    const signInResource = rootResource!.addResource('sign-in', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'sign-in', signInResource, 'sign-in.handler', ['cognito-idp:InitiateAuth'])
  }

  /**
   * It creates /sign-up, /sign-up/confirm, /sign-up/resend-code endpoints
   * @param resourceParams current resource params
   * @returns void
   */
  private static createSignUpResources(resourceParams: ResourceParams): void {
    const { rootResource, defaultCorsPreflightOptions, config } = resourceParams
    const signUpResource = rootResource!.addResource('sign-up', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'sign-up', signUpResource, 'sign-up.handler', ['cognito-idp:SignUp'], {
      rolesConfig: JSON.stringify(config.roles),
    })

    let resource = signUpResource.addResource('confirm', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'sign-up-confirm', resource, 'sign-up-confirm.handler', [
      'cognito-idp:ConfirmSignUp',
    ])

    resource = signUpResource.addResource('resend-code', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'sign-up-resend-code', resource, 'resend-confirmation-code.handler', [
      'cognito-idp:ResendConfirmationCode',
    ])
  }

  /**
   * It creates /token, /token/refresh, /token/revoke endpoints
   * @param resourceParams current resource params
   * @returns void
   */
  private static createTokenResources(resourceParams: ResourceParams): void {
    const { rootResource, defaultCorsPreflightOptions, params } = resourceParams

    const tokenResource = rootResource!.addResource('token', { defaultCorsPreflightOptions })
    // In passwordless mode we'll create an integration to get a valid token after responding to a challenge
    if (params.mode === 'Passwordless') {
      AuthStack.addIntegration(resourceParams, 'token', tokenResource, 'challenge-answer.handler', [
        'cognito-idp:InitiateAuth',
        'cognito-idp:RespondToAuthChallenge',
      ])
    }

    let resource = tokenResource.addResource('refresh', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'refresh-token', resource, 'refresh-token.handler', [
      'cognito-idp:InitiateAuth',
    ])

    resource = tokenResource.addResource('revoke', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'revoke-token', resource, 'sign-out.handler', [
      'cognito-idp:GlobalSignOut',
    ])
  }

  /**
   * It creates /password, /password/forgot, /password/change endpoints
   * @param resourceParams current resource params
   * @returns void
   */
  private static createPasswordResources(resourceParams: ResourceParams): void {
    const { rootResource, defaultCorsPreflightOptions, params } = resourceParams
    if (params.mode !== 'UserPassword') {
      return
    }
    const passwordResource = rootResource!.addResource('password', { defaultCorsPreflightOptions })
    let resource = passwordResource.addResource('forgot', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'forgot-password', resource, 'forgot-password.handler', [
      'cognito-idp:ForgotPassword',
    ])

    resource = passwordResource.addResource('change', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(resourceParams, 'change-password', resource, 'confirm-forgot-password.handler', [
      'cognito-idp:ConfirmForgotPassword',
    ])
  }

  /**
   * It creates an integration between the API and a lambda
   * @param resourceParams current resource params
   * @param name lambda name
   * @param resource the entry endpoint which call the lambda
   * @param handler main lambda function name
   * @param actions lambda Cognito permissions
   * @param env lambda environment variables
   * @returns void
   */
  private static addIntegration(
    resourceParams: ResourceParams,
    name: string,
    resource: Resource,
    handler: string,
    actions: string[],
    env?: Record<string, string>
  ): void {
    const { userPool, rocketStackPrefixId, userPoolClientId, params, stack } = resourceParams

    const authLambda = createLambda(stack, `${rocketStackPrefixId}-${name}`, handler, {
      userPoolId: userPool!.userPoolId,
      userPoolClientId: userPoolClientId!,
      mode: params.mode,
      ...env,
    })

    resource.addMethod('POST', new LambdaIntegration(authLambda))

    authLambda.addToRolePolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: actions,
        resources: [userPool!.userPoolArn],
      })
    )
  }

  /**
   * It prints the following Cloud Formation output vars:
   *  AuthApiEndpoint: Base auth entpoint
   *  AuthApiIssuer: Issuer which sign jwt tokens
   *  AuthApiJwksUri: Uri with the public rsa keys to validate signed tokens
   *  AuthUserPoolId: User pool id, useful for integration tests
   *  AuthUserPoolClientId: User pool client id, useful for integration tests
   * @param resourceParams current resource params
   */
  private static printOutput(resourceParams: ResourceParams): void {
    const { stack, authApi, tokenVerifier } = resourceParams
    new CfnOutput(stack, 'AuthApiEndpoint', {
      value: authApi!.url + 'auth',
      description: 'Auth API endpoint',
    })

    new CfnOutput(stack, 'AuthApiIssuer', {
      value: tokenVerifier?.issuer!,
      description: 'Auth API JWT issuer',
    })

    new CfnOutput(stack, 'AuthApiJwksUri', {
      value: tokenVerifier?.jwksUri!,
      description: 'Auth API JKWS URI',
    })

    new CfnOutput(stack, 'AuthUserPoolId', {
      value: resourceParams.userPool?.userPoolId!,
      description: 'Auth UserPoolId',
    })

    new CfnOutput(stack, 'AuthUserPoolClientId', {
      value: resourceParams.userPoolClientId!,
      description: 'Auth UserPoolClientId',
    })
  }
  /**
   * Helper to generate a tokenVerifier object based on the generated UserPool and the current Stack
   * @param resourceParams current resource params
   * @returns TokenVerifier object, containing the issuer and jwksUri
   */
  private static tokenVerifier(resourceParams: ResourceParams): TokenVerifier {
    const { stack, userPool } = resourceParams

    const issuer = `https://cognito-idp.${stack.region}.${stack.urlSuffix}/${userPool?.userPoolId}`
    const jwksUri = issuer + '/.well-known/jwks.json'

    return {
      issuer,
      jwksUri,
    }
  }
  /**
   * It will setup the issuer and jwksUri in the core lambas where token verification is needed
   * @param resourceParams current resource params
   */
  private static createEnvVars(resourceParams: ResourceParams): void {
    const { stack, tokenVerifier } = resourceParams
    const { issuer, jwksUri } = tokenVerifier!

    const graphQLHandler = stack.node.tryFindChild('graphql-handler') as Function
    graphQLHandler?.addEnvironment(JWT_ENV_VARS.BOOSTER_JWKS_URI, jwksUri)
    graphQLHandler?.addEnvironment(JWT_ENV_VARS.BOOSTER_JWT_ISSUER, issuer)

    const subscriptionHandler = stack.node.tryFindChild('subscriptions-notifier') as Function
    subscriptionHandler?.addEnvironment(JWT_ENV_VARS.BOOSTER_JWKS_URI, jwksUri)
    subscriptionHandler?.addEnvironment(JWT_ENV_VARS.BOOSTER_JWT_ISSUER, issuer)
  }
}
