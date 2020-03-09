import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import {
  CfnApi,
  CfnIntegration,
  CfnRoute,
  CfnAuthorizer,
  CfnRouteResponse, CfnIntegrationResponse
} from '@aws-cdk/aws-apigatewayv2'
import { Code, Function } from '@aws-cdk/aws-lambda'
import { Fn } from '@aws-cdk/core'
import * as params from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'
import { RestApi } from '@aws-cdk/aws-apigateway'

// - On subscribe -> Store connecitonID, user data?, GraphQL subscription(name) and parameters
// - When disconnect, remove this
// - What happens when the token expires? How to handle authentication with websockets?

// Dynamo schema:
// {
// Key: {
//   SubscriptionName,
//   ...SubscriptionParameters,
// }
// ConnectionIDs
// }
//
// Validation should occur, like in commands, so that you can't subscribe to non-allowed Readmodel by role, and non-allowed IDs.
//   This validation is business dependant

interface GraphQLAPIStackMembers {
  graphQLLambda: Function
}

export class GraphQLAPIStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly restAPI: RestApi,
    private readonly websocketAPI: CfnApi
  ) {}

  /*
   - The rest endpoint GraphQL will have the same authorizer as the websocket
   - The authorizer will insert the user in the context as it is doing right now.
   - The routes connect and disconnect will be mock integrations
   - The route $default will be handled by the lambda.
   - The same lambda will be used for the /graphql endpoint
   */

  public build(): GraphQLAPIStackMembers {
    const graphQLLambda = this.buildLambda()
    const lambdaIntegration = this.buildLambdaIntegration(graphQLLambda)
    const mockIntegration = this.buildMockIntegration()
    const authorizer = this.buildAuthorizer()

    const connectRoute = this.buildRoute('$connect', mockIntegration, authorizer)
    this.buildRouteResponse(connectRoute, mockIntegration)
    this.buildRoute('$disconnect', mockIntegration)
    this.buildRoute('$default', lambdaIntegration)
    // Build a GraphQL endpoint /graphql with the same lambda.


    console.log(this.restAPI)

    return { graphQLLambda }
  }

  private buildLambda(): Function {
    const lambdaLocalID = this.config.resourceNames.applicationStack + '-graphql-handler'
    const graphQLLambda = new Function(this.stack, lambdaLocalID, {
      ...params.lambda,
      functionName: lambdaLocalID,
      handler: this.config.serveGraphQLHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })
    graphQLLambda.addPermission(lambdaLocalID + '-invocation-permission', {
      principal: new ServicePrincipal('apigateway.amazonaws.com'),
    })
    return graphQLLambda
  }

  private buildLambdaIntegration(lambda: Function): CfnIntegration {
    const localId = this.config.resourceNames.applicationStack + '-graphql-handler-integration'
    const integration = new CfnIntegration(this.stack, localId, {
      apiId: this.websocketAPI.ref,
      integrationType: 'AWS_PROXY',
      integrationUri: Fn.join('', [
        'arn:',
        Fn.ref('AWS::Partition'),
        ':apigateway:',
        Fn.ref('AWS::Region'),
        ':lambda:path/2015-03-31/functions/',
        lambda.functionArn,
        '/invocations',
      ]),
    })
    integration.addDependsOn(this.websocketAPI)
    return integration
  }

  private buildMockIntegration(): CfnIntegration {
    const integrationLocalId = this.config.resourceNames.applicationStack + '-graphql-mock-integration'
    const integration = new CfnIntegration(this.stack, integrationLocalId, {
      apiId: this.websocketAPI.ref,
      integrationType: 'MOCK',
      templateSelectionExpression: '200',
      requestTemplates: {
        '200': '{"statusCode":200}',
      },
    })
    integration.addDependsOn(this.websocketAPI)
    return integration
  }

  private buildRoute(routeKey: string, integration: CfnIntegration, authorizer?: CfnAuthorizer): CfnRoute {
    const localID = `${this.config.resourceNames.applicationStack}-route-${routeKey}`
    const route = new CfnRoute(this.stack, localID, {
      apiId: this.websocketAPI.ref,
      routeKey: routeKey,
      target: Fn.join('/', ['integrations', integration.ref]),
    })
    if (authorizer) {
      route.authorizationType = 'CUSTOM'
      route.authorizerId = authorizer.ref
    }
    route.addDependsOn(integration)
    return route
  }

  private buildRouteResponse(route: CfnRoute, integration: CfnIntegration): void {
    const localID = `${this.config.resourceNames.applicationStack}-route-${route}-response`
    const routeResponse = new CfnRouteResponse(this.stack, localID, {
      apiId: this.websocketAPI.ref,
      routeId: route.ref,
      routeResponseKey: '$default',
    })
    routeResponse.addDependsOn(route)

    const integrationResponseLocalId = this.config.resourceNames.applicationStack + '-graphql-mock-integration-response'
    const integrationResponse = new CfnIntegrationResponse(this.stack, integrationResponseLocalId, {
      integrationId: integration.ref,
      apiId: this.websocketAPI.ref,
      integrationResponseKey: '$default',
    })
    integrationResponse.addDependsOn(integration)
  }

  private buildAuthorizer(): CfnAuthorizer {
    const lambdaLocalID = this.config.resourceNames.applicationStack + '-lambda-authorizer'
    const lambda = new Function(this.stack, lambdaLocalID, {
      ...params.lambda,
      functionName: lambdaLocalID,
      handler: this.config.authorizerHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })
    lambda.addPermission(lambdaLocalID + '-invocation-permission', {
      principal: new ServicePrincipal('apigateway.amazonaws.com'),
    })

    const authorizerLocalID = this.config.resourceNames.applicationStack + '-authorizer'
    const authorizer = new CfnAuthorizer(this.stack, authorizerLocalID, {
      apiId: this.websocketAPI.ref,
      authorizerType: 'REQUEST',
      name: authorizerLocalID,
      identitySource: ['route.request.header.Authorization'],
      authorizerUri: Fn.join('', [
        'arn:',
        Fn.ref('AWS::Partition'),
        ':apigateway:',
        Fn.ref('AWS::Region'),
        ':lambda:path/2015-03-31/functions/',
        lambda.functionArn,
        '/invocations',
      ]),
    })

    return authorizer
  }
}
