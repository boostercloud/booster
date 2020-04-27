import { BoosterConfig } from '@boostercloud/framework-types'
import { Fn, Stack, Duration, RemovalPolicy } from '@aws-cdk/core'
import {
  CfnAuthorizer,
  CfnIntegration,
  CfnIntegrationResponse,
  CfnRoute,
  CfnRouteResponse,
} from '@aws-cdk/aws-apigatewayv2'
import { Code, Function, IEventSource } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'
import { AuthorizationType, LambdaIntegration, RequestAuthorizer } from '@aws-cdk/aws-apigateway'
import { Cors } from '@aws-cdk/aws-apigateway/lib/cors'
import { Table, AttributeType, BillingMode } from '@aws-cdk/aws-dynamodb'
import {
  subscriptionsStorePartitionKeyAttribute,
  subscriptionsStoreSortKeyAttribute,
  subscriptionsStoreTTLAttribute,
} from '@boostercloud/framework-provider-aws'
import { DynamoEventSource } from '@aws-cdk/aws-lambda-event-sources'
import { APIs } from '../params'

interface GraphQLStackMembers {
  graphQLLambda: Function
  subscriptionDispatcherLambda: Function
  subscriptionsTable: Table
}

export class GraphQLStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs,
    private readonly readModelTables: Array<Table>
  ) {}

  public build(): GraphQLStackMembers {
    const authorizerLambda = this.buildLambda('graphql-authorizer', this.config.authorizerHandler)
    const graphQLLambda = this.buildLambda('graphql-handler', this.config.serveGraphQLHandler)
    const readModelsEventSources = this.buildEventSourcesForTables(this.readModelTables)
    const subscriptionDispatcherLambda = this.buildLambda(
      'subscriptions-notifier',
      this.config.notifySubscribersHandler,
      readModelsEventSources
    )

    this.buildWebsocketRoutes(graphQLLambda, authorizerLambda)
    this.buildRESTRoutes(graphQLLambda, authorizerLambda)
    const subscriptionsTable = this.buildSubscriptionsTable()

    return { graphQLLambda, subscriptionDispatcherLambda, subscriptionsTable }
  }

  private buildLambda(name: string, handler: string, eventSources?: Array<IEventSource>): Function {
    const lambda = new Function(this.stack, name, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: `${this.config.resourceNames.applicationStack}-${name}`,
      handler: handler,
      code: Code.fromAsset(this.config.userProjectRootPath),
      events: eventSources,
    })
    lambda.addPermission(name + '-invocation-permission', {
      principal: new ServicePrincipal('apigateway.amazonaws.com'),
    })

    return lambda
  }

  private buildEventSourcesForTables(readModelTables: Array<Table>): Array<DynamoEventSource> {
    return readModelTables.map((table) => new DynamoEventSource(table, params.stream()))
  }

  private buildWebsocketRoutes(graphQLLambda: Function, authorizerLambda: Function): void {
    const lambdaIntegration = this.buildLambdaIntegration(graphQLLambda)
    const mockIntegration = this.buildMockIntegration()
    const websocketAuthorizer = this.buildWebsocketAuthorizer(authorizerLambda)

    const connectRoute = this.buildRoute('$connect', mockIntegration, websocketAuthorizer)
    this.buildRouteResponse(connectRoute, mockIntegration)
    const defaultRoute = this.buildRoute('$default', lambdaIntegration)
    this.buildRouteResponse(defaultRoute, lambdaIntegration)
    this.buildRoute('$disconnect', lambdaIntegration)
  }

  private buildLambdaIntegration(lambda: Function): CfnIntegration {
    const localID = 'graphql-handler-integration'
    const integration = new CfnIntegration(this.stack, localID, {
      apiId: this.apis.websocketAPI.ref,
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
    integration.addDependsOn(this.apis.websocketAPI)
    return integration
  }

  private buildMockIntegration(): CfnIntegration {
    const localID = 'graphql-mock-integration'
    const integration = new CfnIntegration(this.stack, localID, {
      apiId: this.apis.websocketAPI.ref,
      integrationType: 'MOCK',
      templateSelectionExpression: '200',
      requestTemplates: {
        '200': '{"statusCode":200}',
      },
    })
    integration.addDependsOn(this.apis.websocketAPI)
    return integration
  }

  private buildRoute(routeKey: string, integration: CfnIntegration, authorizer?: CfnAuthorizer): CfnRoute {
    const localID = `route-${routeKey}`
    const route = new CfnRoute(this.stack, localID, {
      apiId: this.apis.websocketAPI.ref,
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
    const localID = `route-${route.routeKey}-response`
    const routeResponse = new CfnRouteResponse(this.stack, localID, {
      apiId: this.apis.websocketAPI.ref,
      routeId: route.ref,
      routeResponseKey: '$default',
    })
    routeResponse.addDependsOn(route)

    const integrationResponseLocalId = `route-${route.routeKey}-integration-response`
    const integrationResponse = new CfnIntegrationResponse(this.stack, integrationResponseLocalId, {
      integrationId: integration.ref,
      apiId: this.apis.websocketAPI.ref,
      integrationResponseKey: '$default',
    })
    integrationResponse.addDependsOn(integration)
  }

  private buildWebsocketAuthorizer(lambda: Function): CfnAuthorizer {
    const localID = 'websocket-authorizer'
    return new CfnAuthorizer(this.stack, localID, {
      apiId: this.apis.websocketAPI.ref,
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
    this.apis.restAPI.root
      .addResource('graphql', {
        defaultCorsPreflightOptions: {
          allowOrigins: Cors.ALL_ORIGINS,
        },
      })
      .addMethod('POST', new LambdaIntegration(graphQLLambda), {
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

  private buildSubscriptionsTable(): Table {
    return new Table(this.stack, this.config.resourceNames.subscriptionsStore, {
      tableName: this.config.resourceNames.subscriptionsStore,
      partitionKey: {
        name: subscriptionsStorePartitionKeyAttribute,
        type: AttributeType.STRING,
      },
      sortKey: {
        name: subscriptionsStoreSortKeyAttribute,
        type: AttributeType.STRING,
      },
      billingMode: BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
      timeToLiveAttribute: subscriptionsStoreTTLAttribute,
    })
  }
}
