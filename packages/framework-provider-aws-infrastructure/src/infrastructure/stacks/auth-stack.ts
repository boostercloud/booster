import { BoosterConfig } from '@boostercloud/framework-types'
import { CfnOutput, Stack } from '@aws-cdk/core'
import { StringAttribute, UserPool, UserPoolClient, UserPoolDomain, VerificationEmailStyle } from '@aws-cdk/aws-cognito'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { APIs } from '../params'
import { Effect, IRole, PolicyDocument, PolicyStatement, Role, ServicePrincipal } from '@aws-cdk/aws-iam'
import { AwsIntegration, Cors, CorsOptions, MethodOptions, PassthroughBehavior } from '@aws-cdk/aws-apigateway'
import { CognitoTemplates } from './api-stack-velocity-templates'

export class AuthStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs
  ) {}

  public build(): UserPool | undefined {
    if (this.config.thereAreRoles) {
      const userPool = this.buildUserPool()
      this.buildUserPoolClient(userPool)
      this.buildAuthAPI(userPool)
      return userPool
    }
    return undefined
  }

  private buildUserPool(): UserPool {
    const localPreSignUpID = 'pre-sign-up-validator'
    const preSignUpLambda = new Function(this.stack, localPreSignUpID, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: this.config.resourceNames.applicationStack + '-' + localPreSignUpID,
      handler: this.config.preSignUpHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    const localUserPoolID = 'user-pool'
    const userPool = new UserPool(this.stack, localUserPoolID, {
      userPoolName: this.config.resourceNames.applicationStack + '-' + localUserPoolID,
      autoVerify: { email: true, phone: true },
      signInAliases: { email: true, phone: true },
      customAttributes: {
        role: new StringAttribute({ mutable: true }),
      },
      selfSignUpEnabled: true,
      userVerification: {
        emailStyle: VerificationEmailStyle.LINK,
        smsMessage: 'The verification code to your new account is {####}',
      },
      lambdaTriggers: {
        preSignUp: preSignUpLambda,
      },
    })

    const localUserPoolDomainID = 'user-pool-domain'
    new UserPoolDomain(this.stack, localUserPoolDomainID, {
      userPool,
      cognitoDomain: { domainPrefix: this.config.resourceNames.applicationStack },
    })

    return userPool
  }

  private buildUserPoolClient(userPool: UserPool): void {
    // Usually, you have multiple clients: one for your WebApp, another for your MobileApp, etc.
    // We could allow defining how many clients the user wants. So far we just create one.
    const localUserPoolClientID = 'user-pool-client'
    const userPoolClient = new UserPoolClient(this.stack, localUserPoolClientID, {
      userPoolClientName: this.config.resourceNames.applicationStack + '-' + localUserPoolClientID,
      userPool,
      authFlows: { userPassword: true },
    })

    new CfnOutput(this.stack, 'clientID', {
      value: userPoolClient.userPoolClientId,
      description: 'Needed for the auth API. This ID must be included in that API under the name "clientID"',
    })
  }

  private buildAuthAPI(userPool: UserPool): void {
    const cognitoIntegrationRole = this.buildCognitoIntegrationRole(userPool)

    const authResource = this.apis.restAPI.root.addResource('auth')
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
    const signUpResource = authResource.addResource('sign-up', { defaultCorsPreflightOptions })
    signUpResource.addMethod('POST', this.buildSignUpIntegration(cognitoIntegrationRole), methodOptions)
    signUpResource
      .addResource('confirm')
      .addMethod('POST', this.buildConfirmSignUpIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-in', { defaultCorsPreflightOptions })
      .addMethod('POST', this.buildSignInIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('refresh-token')
      .addMethod('POST', this.buildRefreshTokenIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-out', { defaultCorsPreflightOptions })
      .addMethod('POST', this.buildSignOutIntegration(cognitoIntegrationRole), methodOptions)
  }

  private buildCognitoIntegrationRole(userPool: UserPool): Role {
    return new Role(this.stack, 'cognito-integration-role', {
      assumedBy: new ServicePrincipal('apigateway.amazonaws.com'),
      inlinePolicies: {
        'cognito-sign': new PolicyDocument({
          statements: [
            new PolicyStatement({
              effect: Effect.ALLOW,
              actions: ['cognito-idp:SignUp', 'cognito-idp:InitiateAuth', 'cognito-idp:GlobalSignOut'],
              resources: [userPool.userPoolArn],
            }),
          ],
        }),
      },
    })
  }

  private buildSignOutIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('GlobalSignOut', withRole, {
      requestTemplate: CognitoTemplates.signOut.request,
      responseTemplate: CognitoTemplates.signOut.response,
    })
  }

  private buildSignUpIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('SignUp', withRole, {
      requestTemplate: CognitoTemplates.signUp.request,
      responseTemplate: CognitoTemplates.signUp.response,
    })
  }

  private buildConfirmSignUpIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('ConfirmSignUp', withRole, {
      requestTemplate: CognitoTemplates.confirmSignUp.request,
      responseTemplate: CognitoTemplates.confirmSignUp.response,
    })
  }

  private buildSignInIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('InitiateAuth', withRole, {
      requestTemplate: CognitoTemplates.signIn.request,
      responseTemplate: CognitoTemplates.signIn.response,
    })
  }

  private buildRefreshTokenIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('InitiateAuth', withRole, {
      requestTemplate: CognitoTemplates.refreshToken.request,
      responseTemplate: CognitoTemplates.refreshToken.response,
    })
  }

  private buildCognitoIntegration(
    forAction: CognitoAuthActions,
    withRole: IRole,
    templates: { requestTemplate: string; responseTemplate: string }
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
              'application/json': templates.responseTemplate,
            },
          },
        ],
        requestTemplates: {
          'application/json': templates.requestTemplate,
        },
      },
    })
  }
}

// Note: InitiateAuth is used for sign-in and refresh-token
type CognitoAuthActions = 'InitiateAuth' | 'SignUp' | 'ConfirmSignUp' | 'GlobalSignOut'
