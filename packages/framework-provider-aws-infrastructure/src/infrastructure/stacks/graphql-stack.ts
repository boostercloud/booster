import { BoosterConfig } from '@boostercloud/framework-types'
import { Fn, Stack, Duration } from '@aws-cdk/core'
import {
  CfnApi,
  CfnAuthorizer,
  CfnIntegration,
  CfnIntegrationResponse,
  CfnRoute,
  CfnRouteResponse,
} from '@aws-cdk/aws-apigatewayv2'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'
import { AuthorizationType, LambdaIntegration, RequestAuthorizer, RestApi } from '@aws-cdk/aws-apigateway'

interface GraphQLStackMembers {
  graphQLLambda: Function
}

export class GraphQLStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly restAPI: RestApi,
    private readonly websocketAPI: CfnApi
  ) {}

  public build(): GraphQLStackMembers {
    const graphQLLambda = this.buildLambda('graphql-handler', this.config.serveGraphQLHandler)
    const authorizerLambda = this.buildLambda('graphql-authorizer', this.config.authorizerHandler)

    this.buildWebsocketRoutes(graphQLLambda, authorizerLambda)
    this.buildRESTRoutes(graphQLLambda, authorizerLambda)

    return { graphQLLambda }
  }

  private buildLambda(name: string, handler: string): Function {
    const lambda = new Function(this.stack, name, {
      ...params.lambda,
      functionName: `${this.config.resourceNames.applicationStack}-${name}`,
      handler: handler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })
    lambda.addPermission(name + '-invocation-permission', {
      principal: new ServicePrincipal('apigateway.amazonaws.com'),
    })

    return lambda
  }

  private buildWebsocketRoutes(graphQLLambda: Function, authorizerLambda: Function): void {
    const lambdaIntegration = this.buildLambdaIntegration(graphQLLambda)
    const mockIntegration = this.buildMockIntegration()
    const websocketAuthorizer = this.buildWebsocketAuthorizer(authorizerLambda)

    const connectRoute = this.buildRoute('$connect', mockIntegration, websocketAuthorizer)
    this.buildRouteResponse(connectRoute, mockIntegration)
    this.buildRoute('$disconnect', mockIntegration)
    this.buildRoute('$default', lambdaIntegration)
  }

  private buildLambdaIntegration(lambda: Function): CfnIntegration {
    const localID = 'graphql-handler-integration'
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
    const localID = 'graphql-mock-integration'
    const integration = new CfnIntegration(this.stack, localID, {
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
    const localID = `route-${routeKey}`
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
    const localID = `route-${route}-response`
    const routeResponse = new CfnRouteResponse(this.stack, localID, {
      apiId: this.websocketAPI.ref,
      routeId: route.ref,
      routeResponseKey: '$default',
    })
    routeResponse.addDependsOn(route)

    const integrationResponseLocalId = 'graphql-mock-integration-response'
    const integrationResponse = new CfnIntegrationResponse(this.stack, integrationResponseLocalId, {
      integrationId: integration.ref,
      apiId: this.websocketAPI.ref,
      integrationResponseKey: '$default',
    })
    integrationResponse.addDependsOn(integration)
  }

  private buildWebsocketAuthorizer(lambda: Function): CfnAuthorizer {
    const localID = 'websocket-authorizer'
    return new CfnAuthorizer(this.stack, localID, {
      apiId: this.websocketAPI.ref,
      authorizerType: 'REQUEST',
      name: localID,
      identitySource: [],
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
  }

  private buildRESTRoutes(graphQLLambda: Function, authorizerLambda: Function): void {
    const restAuthorizer = this.buildRESTAuthorizer(authorizerLambda)
    this.restAPI.root.addResource('graphql').addMethod('POST', new LambdaIntegration(graphQLLambda), {
      authorizationType: AuthorizationType.CUSTOM,
      authorizer: restAuthorizer,
    })
  }

  private buildRESTAuthorizer(lambda: Function): RequestAuthorizer {
    const localID = 'rest-authorizer'
    return new RequestAuthorizer(this.stack, localID, {
      handler: lambda,
      resultsCacheTtl: Duration.seconds(0),
      identitySources: [],
    })
  }
}
