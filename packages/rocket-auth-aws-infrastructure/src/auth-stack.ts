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
import { Cors, CorsOptions, LambdaIntegration, MethodOptions, RestApi } from '@aws-cdk/aws-apigateway'
import { createLamba } from './utils'

export interface AWSAuthRocketParams {
  appName: string
  environmentName: string
  passwordPolicy?: {
    requireDigits: boolean
    requireLowercase: boolean
    requireSymbols: boolean
    requireUppercase: boolean
  }
  mode: 'Passwordless' | 'UserPassword'
}

export class AuthStack {
  public static mountStack(params: AWSAuthRocketParams, stack: Stack): void {
    const userPool = AuthStack.buildUserPool(params, stack)
    const userPoolClient = AuthStack.buildUserPoolClient(params, stack, userPool)
    const authApi = AuthStack.buildAuthAPI(params, stack, userPool, userPoolClient.userPoolClientId)
    AuthStack.printOutput(stack, userPool.userPoolId, authApi)
  }

  public static unmountStack?(): void {}

  private static rocketArtifactsPrefix(params: AWSAuthRocketParams): string {
    return `${params.appName}-${params.environmentName}-rocket-auth`
  }

  private static buildUserPool(params: AWSAuthRocketParams, stack: Stack): UserPool {
    const userPoolID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool`

    const lambdaTriggers = AuthStack.buildLambdaTriggers(params, stack)
    const useEmail = params.mode === 'UserPassword'
    const userPool = new UserPool(stack, userPoolID, {
      userPoolName: userPoolID,
      signInAliases: {
        email: useEmail,
        phone: !useEmail,
      },
      autoVerify: {
        email: useEmail,
        phone: !useEmail,
      },
      customAttributes: {
        role: new StringAttribute({ mutable: true }),
      },
      selfSignUpEnabled: true,
      passwordPolicy: params.passwordPolicy,
      userVerification: {
        emailStyle: VerificationEmailStyle.LINK,
      },
      lambdaTriggers,
    })

    const localUserPoolDomainID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool-domain`
    new UserPoolDomain(stack, localUserPoolDomainID, {
      userPool,
      cognitoDomain: { domainPrefix: params.appName },
    })

    return userPool
  }

  private static buildLambdaTriggers(params: AWSAuthRocketParams, stack: Stack): UserPoolTriggers {
    const createAuthChallenge = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-create-auth-challenge`,
      'create.handler',
      'lambdas/challenge'
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
      'define.handler',
      'lambdas/challenge'
    )

    const verifyAuthChallengeResponse = createLamba(
      stack,
      `${AuthStack.rocketArtifactsPrefix(params)}-verify-auth-challenge`,
      'verify.handler',
      'lambdas/challenge'
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
      deployOptions: { stageName: params.environmentName },
    })

    const authResource = rootAuthAPI.root.addResource('auth')

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
    const defaultCorsPreflightOptions: CorsOptions = {
      allowHeaders: ['*'],
      allowOrigins: Cors.ALL_ORIGINS,
      allowMethods: ['POST', 'OPTIONS'],
    }

    const isUserPasswordMode = params.mode === 'UserPassword'

    const endpointIntegrations = [
      {
        endpoint: 'sign-in',
        handler: 'sign-in.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:InitiateAuth'],
        included: true,
      },
      {
        endpoint: 'sign-up',
        handler: 'sign-up.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:SignUp'],
        included: true,
      },
      {
        endpoint: 'verify-code',
        handler: 'answer.handler',
        path: 'lambdas/challenge',
        actions: ['cognito-idp:InitiateAuth', 'cognito-idp:RespondToAuthChallenge'],
        included: true,
      },
      {
        endpoint: 'confirm-sign-up',
        handler: 'confirm.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:ConfirmSignUp'],
        included: true,
      },
      {
        endpoint: 'resend-confirmation-code',
        handler: 'resend.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:ResendConfirmationCode'],
        included: true,
      },
      {
        endpoint: 'refresh-token',
        handler: 'refresh.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:InitiateAuth'],
        included: true,
      },
      {
        endpoint: 'forgot-password',
        handler: 'forgot.handler',
        path: 'lambdas/forgot',
        actions: ['cognito-idp:ForgotPassword'],
        included: isUserPasswordMode,
      },
      {
        endpoint: 'confirm-forgot-password',
        handler: 'confirm.handler',
        path: 'lambdas/forgot',
        actions: ['cognito-idp:ConfirmForgotPassword'],
        included: isUserPasswordMode,
      },
      {
        endpoint: 'sign-out',
        handler: 'sign-out.handler',
        path: 'lambdas/common',
        actions: ['cognito-idp:GlobalSignOut'],
        included: true,
      },
    ]

    endpointIntegrations
      .filter((item) => item.included)
      .map((item) => {
        const authLambda = createLamba(
          stack,
          `${AuthStack.rocketArtifactsPrefix(params)}-${item.endpoint}`,
          item.handler,
          item.path,
          {
            userPoolId: userPool.userPoolId,
            userPoolClientId: userPoolClientId,
            mode: params.mode,
          }
        )
        authResource
          .addResource(item.endpoint, { defaultCorsPreflightOptions })
          .addMethod('POST', new LambdaIntegration(authLambda), methodOptions)

        authLambda.addToRolePolicy(
          new PolicyStatement({
            effect: Effect.ALLOW,
            actions: item.actions,
            resources: [userPool.userPoolArn],
          })
        )
      })

    return rootAuthAPI
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
