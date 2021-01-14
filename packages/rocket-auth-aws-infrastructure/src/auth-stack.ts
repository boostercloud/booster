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

    const useEmail = params.mode === 'UserPassword'
    const lambdaTriggers = useEmail ? undefined : AuthStack.buildLambdaTriggers(params, stack)

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

    const endpointIntegrations = [
      {
        endpoint: 'sign-in',
        handler: 'sign-in.handler',
        actions: ['cognito-idp:InitiateAuth'],
        included: true,
      },
      {
        endpoint: 'sign-up',
        handler: 'sign-up.handler',
        actions: ['cognito-idp:SignUp'],
        included: true,
      },
      {
        endpoint: 'confirm',
        handler: 'sign-up-confirm.handler',
        actions: ['cognito-idp:ConfirmSignUp'],
        included: true,
        parent: 'sign-up',
      },
      {
        endpoint: 'resend-code',
        handler: 'resend-confirmation-code.handler',
        actions: ['cognito-idp:ResendConfirmationCode'],
        included: true,
        parent: 'sign-up',
      },
      {
        endpoint: 'token',
        handler: 'challenge-answer.handler',
        actions: ['cognito-idp:InitiateAuth', 'cognito-idp:RespondToAuthChallenge'],
        included: params.mode === 'Passwordless',
      },
      {
        endpoint: 'refresh',
        handler: 'refresh-token.handler',
        actions: ['cognito-idp:InitiateAuth'],
        included: true,
        parent: 'token',
      },
      {
        endpoint: 'token/revoke',
        handler: 'sign-out.handler',
        actions: ['cognito-idp:GlobalSignOut'],
        included: true,
        parent: 'token',
      },
      {
        endpoint: 'forgot',
        handler: 'forgot-password.handler',
        actions: ['cognito-idp:ForgotPassword'],
        included: params.mode === 'UserPassword',
        parent: 'password',
      },
      {
        endpoint: 'confirm',
        handler: 'confirm-forgot-password.handler',
        actions: ['cognito-idp:ConfirmForgotPassword'],
        included: params.mode === 'UserPassword',
        parent: 'password',
      },
    ]

    endpointIntegrations
      .filter((item) => item.included)
      .map((item) => {
        const authLambda = createLamba(
          stack,
          `${AuthStack.rocketArtifactsPrefix(params)}-${item.endpoint}`,
          item.handler,
          {
            userPoolId: userPool.userPoolId,
            userPoolClientId: userPoolClientId,
            mode: params.mode,
          }
        )

        let resource = authResource
        if (item.parent) {
          resource = authResource.resourceForPath(item.parent)
        }
        resource
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
