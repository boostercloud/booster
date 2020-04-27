import { BoosterConfig } from '@boostercloud/framework-types'
import { CfnOutput, RemovalPolicy, Stack } from '@aws-cdk/core'
import { AuthFlow, CfnUserPool, CfnUserPoolDomain, UserPoolAttribute, UserPoolClient } from '@aws-cdk/aws-cognito'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { Effect, IRole, PolicyDocument, PolicyStatement, Role, ServicePrincipal } from '@aws-cdk/aws-iam'
import { AwsIntegration, PassthroughBehavior } from '@aws-cdk/aws-apigateway'
import { CognitoTemplates } from './api-stack-velocity-templates'
import { APIs } from '../params'

export class AuthStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs
  ) {}

  public build(): void {
    if (this.config.thereAreRoles) {
      const userPool = this.buildUserPool()
      this.buildUserPoolClient(userPool)
      this.buildAuthAPI()
    }
  }

  private buildUserPool(): CfnUserPool {
    const localPreSignUpID = 'pre-sign-up-validator'
    const preSignUpLambda = new Function(this.stack, localPreSignUpID, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: this.config.resourceNames.applicationStack + '-' + localPreSignUpID,
      handler: this.config.preSignUpHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    const localUserPoolID = 'user-pool'
    const userPool = new CfnUserPool(this.stack, localUserPoolID, {
      userPoolName: this.config.resourceNames.applicationStack + '-' + localUserPoolID,
      autoVerifiedAttributes: [UserPoolAttribute.EMAIL],
      schema: [
        {
          attributeDataType: 'String',
          mutable: true,
          name: 'roles',
        },
      ],
      usernameAttributes: [UserPoolAttribute.EMAIL],
      verificationMessageTemplate: {
        defaultEmailOption: 'CONFIRM_WITH_LINK',
      },
      lambdaConfig: {
        preSignUp: preSignUpLambda.functionArn,
      },
    })

    preSignUpLambda.addPermission(localPreSignUpID + '-user-pool-permission', {
      principal: new ServicePrincipal('cognito-idp.amazonaws.com'),
      sourceArn: userPool.attrArn,
    })

    const localUserPoolDomainID = 'user-pool-domain'
    new CfnUserPoolDomain(this.stack, localUserPoolDomainID, {
      userPoolId: userPool.ref,
      domain: this.config.resourceNames.applicationStack,
    }).applyRemovalPolicy(RemovalPolicy.DESTROY)

    return userPool
  }

  private buildUserPoolClient(userPool: CfnUserPool): void {
    // Usually, you have multiple clients: one for your WebApp, another for your MobileApp, etc.
    // We could allow defining how many clients the user wants. So far we just create one.
    const localUserPoolClientID = 'user-pool-client'
    const userPoolClient = new UserPoolClient(this.stack, localUserPoolClientID, {
      userPoolClientName: this.config.resourceNames.applicationStack + '-' + localUserPoolClientID,
      userPool: {
        node: userPool.node,
        stack: this.stack,
        userPoolArn: userPool.attrArn,
        userPoolId: userPool.ref,
        userPoolProviderName: userPool.attrProviderName,
        userPoolProviderUrl: userPool.attrProviderUrl,
      },
      enabledAuthFlows: [AuthFlow.USER_PASSWORD],
    })

    new CfnOutput(this.stack, 'clientID', {
      value: userPoolClient.userPoolClientId,
      description: 'Needed for the auth API. This ID must be included in that API under the name "clientID"',
    })
  }

  private buildAuthAPI(): void {
    const cognitoIntegrationRole = this.buildCognitoIntegrationRole()

    const authResource = this.apis.restAPI.root.addResource('auth')
    const methodOptions = {
      methodResponses: [
        {
          statusCode: '200',
        },
        {
          statusCode: '400',
        },
        {
          statusCode: '500',
        },
      ],
    }
    authResource
      .addResource('sign-up')
      .addMethod('POST', this.buildSignUpIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-in')
      .addMethod('POST', this.buildSignInIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-out')
      .addMethod('POST', this.buildSignOutIntegration(cognitoIntegrationRole), methodOptions)
  }

  private buildCognitoIntegrationRole(): Role {
    return new Role(this.stack, 'cognito-integration-role', {
      assumedBy: new ServicePrincipal('apigateway.amazonaws.com'),
      inlinePolicies: {
        'cognito-sign': new PolicyDocument({
          statements: [
            new PolicyStatement({
              effect: Effect.ALLOW,
              actions: ['cognito-idp:SignUp', 'cognito-idp:InitiateAuth', 'cognito-idp:GlobalSignOut'],
              resources: ['*'],
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

  private buildSignInIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('InitiateAuth', withRole, {
      requestTemplate: CognitoTemplates.signIn.request,
      responseTemplate: CognitoTemplates.signIn.response,
    })
  }

  private buildCognitoIntegration(
    forAction: CognitoAuthActions,
    withRole: IRole,
    templates: { requestTemplate: string; responseTemplate: string }
  ): AwsIntegration {
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
          },
          {
            selectionPattern: '4\\d\\d',
            statusCode: '400',
          },
          {
            selectionPattern: '2\\d\\d',
            statusCode: '200',
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

type CognitoAuthActions = 'InitiateAuth' | 'SignUp' | 'GlobalSignOut'
