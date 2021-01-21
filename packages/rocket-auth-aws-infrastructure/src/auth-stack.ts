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
import { Cors, CorsOptions, LambdaIntegration, MethodOptions, Resource, RestApi } from '@aws-cdk/aws-apigateway'
import { createLamba } from './utils'
import { BoosterConfig } from '@boostercloud/framework-types'

export interface AWSAuthRocketParams {
  passwordPolicy?: {
    minLength?: number
    requireDigits: boolean
    requireLowercase: boolean
    requireSymbols: boolean
    requireUppercase: boolean
  }
  mode: 'Passwordless' | 'UserPassword'
  config: BoosterConfig
}

export class AuthStack {
  public static mountStack(params: AWSAuthRocketParams, stack: Stack): void {
    if (params.config.thereAreRoles) {
      const userPool = AuthStack.buildUserPool(params, stack)
      const userPoolClient = AuthStack.buildUserPoolClient(params, stack, userPool)
      const authApi = AuthStack.buildAuthAPI(params, stack, userPool, userPoolClient.userPoolClientId)
      AuthStack.printOutput(stack, userPool.userPoolId, authApi)
    }
  }

  public static unmountStack?(): void {}

  public static rocketArtifactsPrefix(params: AWSAuthRocketParams): string {
    return `${params.config.appName}-${params.config.environmentName}-rocket-auth`
  }

  private static buildUserPool(params: AWSAuthRocketParams, stack: Stack): UserPool {
    const userPoolID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool`

    const userPasswordMode = params.mode === 'UserPassword'
    const lambdaTriggers = userPasswordMode ? undefined : AuthStack.buildLambdaTriggers(params, stack)
    const preSignUpTrigger = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-pre-sign-up`,
      'pre-sign-up.handler',
      {
        rolesConfig: JSON.stringify(params.config.roles),
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
      passwordPolicy: userPasswordMode
        ? params.passwordPolicy
        : { requireDigits: false, requireLowercase: false, requireUppercase: false, requireSymbols: false },
      userVerification: {
        emailStyle: VerificationEmailStyle.LINK,
      },
      lambdaTriggers: {
        ...lambdaTriggers,
        preSignUp: preSignUpTrigger,
      },
    })

    const localUserPoolDomainID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool-domain`
    new UserPoolDomain(stack, localUserPoolDomainID, {
      userPool,
      cognitoDomain: { domainPrefix: params.config.appName },
    })

    return userPool
  }

  public static buildLambdaTriggers(params: AWSAuthRocketParams, stack: Stack): UserPoolTriggers {
    const createAuthChallenge = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-create-auth-challenge`,
      'challenge-create.handler'
    )

    createAuthChallenge.addToRolePolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['sns:Publish'],
        resources: ['*'],
      })
    )

    const defineAuthChallenge = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-define-auth-challenge`,
      'challenge-define.handler'
    )

    const verifyAuthChallengeResponse = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-verify-auth-challenge`,
      'challenge-verify.handler'
    )

    return {
      createAuthChallenge,
      defineAuthChallenge,
      verifyAuthChallengeResponse,
    }
  }

  private static buildUserPoolClient(params: AWSAuthRocketParams, stack: Stack, userPool: UserPool): UserPoolClient {
    const userPoolClientID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool-client`

    const isPasswordless = params.mode == 'Passwordless'

    return new UserPoolClient(stack, userPoolClientID, {
      userPoolClientName: userPoolClientID,
      userPool,
      authFlows: { userPassword: !isPasswordless, custom: isPasswordless },
    })
  }

  private static buildAuthAPI(
    params: AWSAuthRocketParams,
    stack: Stack,
    userPool: UserPool,
    userPoolClientId: string
  ): RestApi {
    const rootAuthAPI = new RestApi(stack, `${AuthStack.rocketArtifactsPrefix(params)}-api`, {
      deployOptions: { stageName: params.config.environmentName },
    })

    const authResource = rootAuthAPI.root.addResource('auth')

    const defaultCorsPreflightOptions: CorsOptions = {
      allowHeaders: ['*'],
      allowOrigins: Cors.ALL_ORIGINS,
      allowMethods: ['POST', 'OPTIONS'],
    }

    // sign-in
    const signInResource = authResource.addResource('sign-in', { defaultCorsPreflightOptions })
    AuthStack.addIntegration('sign-in', stack, params, userPool, userPoolClientId, signInResource, 'sign-in.handler', [
      'cognito-idp:InitiateAuth',
    ])

    // sign-up
    // sign-up/confirm
    // sign-up/resend-code

    const signUpResource = authResource.addResource('sign-up', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(
      'sign-up',
      stack,
      params,
      userPool,
      userPoolClientId,
      signUpResource,
      'sign-up.handler',
      ['cognito-idp:SignUp'],
      { rolesConfig: JSON.stringify(params.config.roles) }
    )

    let resource = signUpResource.addResource('confirm', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(
      'sign-up-confirm',
      stack,
      params,
      userPool,
      userPoolClientId,
      resource,
      'sign-up-confirm.handler',
      ['cognito-idp:ConfirmSignUp']
    )

    resource = signUpResource.addResource('resend-code', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(
      'sign-up-resend-code',
      stack,
      params,
      userPool,
      userPoolClientId,
      resource,
      'resend-confirmation-code.handler',
      ['cognito-idp:ResendConfirmationCode']
    )

    // token
    // token/refresh
    // token/revoke
    const tokenResource = authResource.addResource('token', { defaultCorsPreflightOptions })
    // In passwordless mode we'll have an integration to get a valid token responding to a challenge
    if (params.mode === 'Passwordless') {
      AuthStack.addIntegration(
        'token',
        stack,
        params,
        userPool,
        userPoolClientId,
        tokenResource,
        'challenge-answer.handler',
        ['cognito-idp:InitiateAuth', 'cognito-idp:RespondToAuthChallenge']
      )
    }

    resource = tokenResource.addResource('refresh', { defaultCorsPreflightOptions })
    AuthStack.addIntegration(
      'refresh-token',
      stack,
      params,
      userPool,
      userPoolClientId,
      resource,
      'refresh-token.handler',
      ['cognito-idp:InitiateAuth']
    )

    resource = tokenResource.addResource('revoke', { defaultCorsPreflightOptions })
    AuthStack.addIntegration('revoke-token', stack, params, userPool, userPoolClientId, resource, 'sign-out.handler', [
      'cognito-idp:GlobalSignOut',
    ])

    // password/forgot
    // password/change
    if (params.mode === 'UserPassword') {
      const passwordResource = authResource.addResource('password', { defaultCorsPreflightOptions })
      resource = passwordResource.addResource('forgot', { defaultCorsPreflightOptions })
      AuthStack.addIntegration(
        'forgot-password',
        stack,
        params,
        userPool,
        userPoolClientId,
        resource,
        'forgot-password.handler',
        ['cognito-idp:ForgotPassword']
      )

      resource = passwordResource.addResource('change', { defaultCorsPreflightOptions })
      AuthStack.addIntegration(
        'change-password',
        stack,
        params,
        userPool,
        userPoolClientId,
        resource,
        'confirm-forgot-password.handler',
        ['cognito-idp:ConfirmForgotPassword']
      )
    }
    return rootAuthAPI
  }

  private static addIntegration(
    name: string,
    stack: Stack,
    params: AWSAuthRocketParams,
    userPool: UserPool,
    userPoolClientId: string,
    resource: Resource,
    handler: string,
    actions: string[],
    env?: Record<string, string>
  ): void {
    const allowedOriginHeaderForCors = {
      'method.response.header.Access-Control-Allow-Origin': true,
    }
    const methodOptions: MethodOptions = {
      methodResponses: [
        {
          statusCode: '200',
          responseParameters: allowedOriginHeaderForCors,
        },
        {
          statusCode: '400',
          responseParameters: allowedOriginHeaderForCors,
        },
        {
          statusCode: '500',
          responseParameters: allowedOriginHeaderForCors,
        },
      ],
    }

    const authLambda = createLamba(stack, `${AuthStack.rocketArtifactsPrefix(params)}-${name}`, handler, {
      userPoolId: userPool.userPoolId,
      userPoolClientId: userPoolClientId,
      mode: params.mode,
      ...env,
    })

    resource.addMethod('POST', new LambdaIntegration(authLambda), methodOptions)

    authLambda.addToRolePolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: actions,
        resources: [userPool.userPoolArn],
      })
    )
  }

  private static printOutput(stack: Stack, userPoolId: string, authApi: RestApi): void {
    new CfnOutput(stack, 'AuthApiEndpoint', {
      value: authApi.url + 'auth',
      description: 'Auth api endpoint',
    })

    const issuer = `https://cognito-idp.${stack.region}.${stack.urlSuffix}/${userPoolId}`

    new CfnOutput(stack, 'AuthApiIssuer', {
      value: issuer,
      description: 'Auth api JWT issuer',
    })

    new CfnOutput(stack, 'AuthApiJwksUri', {
      value: issuer + '/.well-known/jwks.json',
      description: 'Auth api JKWS uri',
    })
  }
}
