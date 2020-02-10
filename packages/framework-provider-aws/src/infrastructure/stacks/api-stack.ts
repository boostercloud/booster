import { BoosterConfig } from '@boostercloud/framework-types'
import { CfnOutput, Stack } from '@aws-cdk/core'
import { AwsIntegration, LambdaIntegration, PassthroughBehavior, RestApi } from '@aws-cdk/aws-apigateway'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { Effect, IRole, PolicyDocument, PolicyStatement, Role, ServicePrincipal } from '@aws-cdk/aws-iam'
import { CognitoTemplates } from './api-stack-velocity-templates'

interface ApiStackMembers {
  rootApi: RestApi
  commandsLambda: Function
  readModelFetcherLambda: Function
}

export class ApiStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): ApiStackMembers {
    const rootApi = this.buildRootApi()

    if (this.config.thereAreRoles) {
      this.buildAuthApi(rootApi)
    }
    const commandsLambda = this.buildCommandsAPI(rootApi)
    const readModelFetcherLambda = this.buildReadModelsAPI(rootApi)

    return {
      rootApi,
      commandsLambda,
      readModelFetcherLambda,
    }
  }

  private buildRootApi(): RestApi {
    const rootApi = new RestApi(this.stack, this.config.resourceNames.applicationStack + '-api')

    new CfnOutput(this.stack, 'baseURL', {
      value: rootApi.url,
      description: 'The base URL for all the endpoints of your application',
    })

    return rootApi
  }

  private buildAuthApi(rootApi: RestApi): void {
    const cognitoIntegrationRole = this.buildCognitoIntegrationRole()

    const authResource = rootApi.root.addResource('auth')
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
      .addMethod('POST', ApiStack.buildSignUpIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-in')
      .addMethod('POST', ApiStack.buildSignInIntegration(cognitoIntegrationRole), methodOptions)
    authResource
      .addResource('sign-out')
      .addMethod('POST', ApiStack.buildSignOutIntegration(cognitoIntegrationRole), methodOptions)
  }

  private static buildSignOutIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('GlobalSignOut', withRole, {
      requestTemplate: CognitoTemplates.signOut.request,
      responseTemplate: CognitoTemplates.signOut.response,
    })
  }

  private static buildSignUpIntegration(withRole: IRole): AwsIntegration {
    // This template is a bit more complex because we are transforming the attribute 'roles' from an array to a
    // comma-separated string
    return this.buildCognitoIntegration('SignUp', withRole, {
      requestTemplate: CognitoTemplates.signUp.request,
      responseTemplate: CognitoTemplates.signUp.response,
    })
  }

  private static buildSignInIntegration(withRole: IRole): AwsIntegration {
    return this.buildCognitoIntegration('InitiateAuth', withRole, {
      requestTemplate: CognitoTemplates.signIn.request,
      responseTemplate: CognitoTemplates.signIn.response,
    })
  }

  private static buildCognitoIntegration(
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

  private buildCommandsAPI(api: RestApi): Function {
    const localID = 'commands-main'
    const lambdaFunction = new Function(this.stack, localID, {
      ...params.lambda,
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.commandDispatcherHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    api.root.addResource('commands').addMethod('POST', new LambdaIntegration(lambdaFunction))
    return lambdaFunction
  }

  private buildReadModelsAPI(api: RestApi): Function {
    const localID = 'read-model-fetcher'
    const readModelFetcherLambda = new Function(this.stack, localID, {
      ...params.lambda,
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.readModelMapperHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    const lambdaIntegration = new LambdaIntegration(readModelFetcherLambda)
    const readModelResource = api.root.resourceForPath('readmodels/{readModelName}')
    readModelResource.addMethod('GET', lambdaIntegration)
    readModelResource.addResource('{id}').addMethod('GET', lambdaIntegration)

    return readModelFetcherLambda
  }
}

type CognitoAuthActions = 'InitiateAuth' | 'SignUp' | 'GlobalSignOut'
