import { CfnOutput, Stack } from '@aws-cdk/core'
import { StringAttribute, UserPool, UserPoolClient, VerificationEmailStyle, UserPoolDomain } from '@aws-cdk/aws-cognito'
import { Effect, IRole, PolicyDocument, PolicyStatement, Role, ServicePrincipal } from '@aws-cdk/aws-iam'
import { AwsIntegration, Cors, CorsOptions, MethodOptions, PassthroughBehavior, RestApi } from '@aws-cdk/aws-apigateway'
import {
  CognitoAuthTemplate,
  confirmForgotPasswordTemplate,
  forgotPasswordTemplate,
  refreshTokenTemplate,
  signInTemplate,
  signOutTemplate,
  signUpTemplate,
} from './auth-stack-templates'

type CognitoAuthActions =
  | 'InitiateAuth'
  | 'SignUp'
  | 'ConfirmSignUp'
  | 'GlobalSignOut'
  | 'ForgotPassword'
  | 'ConfirmForgotPassword'

export interface AWSAuthRocketParams {
  appName: string
  environmentName: string
  passwordPolicy?: {
    requireDigits: boolean
    requireLowercase: boolean
    requireSymbols: boolean
    requireUppercase: boolean
  }
  usePhone: boolean
  useEmail: boolean
  autoVerifyEmail: boolean
  autoVerifyPhone: boolean
  emailConfirmationSubject?: string
  emailConfirmationBody?: string
  smsConfirmationMessage?: string
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
    const userPool = new UserPool(stack, userPoolID, {
      userPoolName: userPoolID,
      signInAliases: {
        email: params.useEmail,
        phone: params.usePhone,
      },
      autoVerify: {
        email: params.autoVerifyEmail,
        phone: params.autoVerifyPhone,
      },
      customAttributes: {
        role: new StringAttribute({ mutable: true }),
      },
      selfSignUpEnabled: true,
      passwordPolicy: params.passwordPolicy,
      userVerification: {
        emailStyle: VerificationEmailStyle.LINK,
        emailSubject: params.emailConfirmationSubject,
        emailBody: params.emailConfirmationBody,
        smsMessage: params.smsConfirmationMessage,
      },
    })

    const localUserPoolDomainID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool-domain`
    new UserPoolDomain(stack, localUserPoolDomainID, {
      userPool,
      cognitoDomain: { domainPrefix: params.appName },
    })

    return userPool
  }

  private static buildUserPoolClient(params: AWSAuthRocketParams, stack: Stack, userPool: UserPool): UserPoolClient {
    const userPoolClientID = `${AuthStack.rocketArtifactsPrefix(params)}-user-pool-client`
    return new UserPoolClient(stack, userPoolClientID, {
      userPoolClientName: userPoolClientID,
      userPool,
      authFlows: { userPassword: true },
    })
  }

  private static buildAuthAPI(
    params: AWSAuthRocketParams,
    stack: Stack,
    userPool: UserPool,
    userPoolClientId: string
  ): RestApi {
    const cognitoIntegrationRole = AuthStack.buildCognitoIntegrationRole(stack, userPool)

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

    const endpointIntegrations: Array<{
      endpoint: string
      action: CognitoAuthActions
      template: CognitoAuthTemplate
    }> = [
      {
        endpoint: 'sign-up',
        action: 'SignUp',
        template: signUpTemplate(userPoolClientId),
      },
      {
        endpoint: 'sign-in',
        action: 'InitiateAuth',
        template: signInTemplate(userPoolClientId),
      },
      {
        endpoint: 'refresh-token',
        action: 'InitiateAuth',
        template: refreshTokenTemplate(userPoolClientId),
      },
      {
        endpoint: 'sign-out',
        action: 'GlobalSignOut',
        template: signOutTemplate(),
      },
      {
        endpoint: 'forgot-password',
        action: 'ForgotPassword',
        template: forgotPasswordTemplate(userPoolClientId),
      },
      {
        endpoint: 'confirm-forgot-password',
        action: 'ConfirmForgotPassword',
        template: confirmForgotPasswordTemplate(userPoolClientId),
      },
    ]

    endpointIntegrations.map((item) => {
      authResource
        .addResource(item.endpoint, { defaultCorsPreflightOptions })
        .addMethod(
          'POST',
          AuthStack.buildCognitoIntegration(item.action, cognitoIntegrationRole, item.template),
          methodOptions
        )
    })

    return rootAuthAPI
  }

  private static buildCognitoIntegrationRole(stack: Stack, userPool: UserPool): Role {
    return new Role(stack, 'cognito-integration-role', {
      assumedBy: new ServicePrincipal('apigateway.amazonaws.com'),
      inlinePolicies: {
        'cognito-sign': new PolicyDocument({
          statements: [
            new PolicyStatement({
              effect: Effect.ALLOW,
              actions: [
                'cognito-idp:SignUp',
                'cognito-idp:InitiateAuth',
                'cognito-idp:GlobalSignOut',
                'cognito-idp:ForgotPassword',
                'cognito-idp:ConfirmForgotPassword',
              ],
              resources: [userPool.userPoolArn],
            }),
          ],
        }),
      },
    })
  }

  private static buildCognitoIntegration(
    forAction: CognitoAuthActions,
    withRole: IRole,
    template: { request: string; response: string }
  ): AwsIntegration {
    const responseParameters = {
      ['method.response.header.Access-Control-Allow-Origin']: "'*'",
    }
    return new AwsIntegration({
      service: 'cognito-idp',
      action: forAction,
      integrationHttpMethod: 'POST',
      options: {
        credentialsRole: withRole,
        passthroughBehavior: PassthroughBehavior.NEVER,
        integrationResponses: [
          {
            selectionPattern: '5\\d\\d',
            statusCode: '500',
            responseParameters,
          },
          {
            selectionPattern: '4\\d\\d',
            statusCode: '400',
            responseParameters,
          },
          {
            selectionPattern: '2\\d\\d',
            statusCode: '200',
            responseParameters,
            responseTemplates: {
              'application/json': template.response,
            },
          },
        ],
        requestTemplates: {
          'application/json': template.request,
        },
      },
    })
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
