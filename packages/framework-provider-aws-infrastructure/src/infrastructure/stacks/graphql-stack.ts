import { BoosterConfig } from '@boostercloud/framework-types'
import { Duration, Fn, RemovalPolicy, Stack } from '@aws-cdk/core'
import { CfnAuthorizer, CfnIntegration, CfnIntegrationResponse, CfnRoute } from '@aws-cdk/aws-apigatewayv2'
import { Code, Function, IEventSource } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { APIs } from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'
import { AuthorizationType, LambdaIntegration, RequestAuthorizer } from '@aws-cdk/aws-apigateway'
import { Cors } from '@aws-cdk/aws-apigateway/lib/cors'
import { AttributeType, BillingMode, ProjectionType, Table } from '@aws-cdk/aws-dynamodb'
import { subscriptionsStoreAttributes } from '@boostercloud/framework-provider-aws'
import { DynamoEventSource } from '@aws-cdk/aws-lambda-event-sources'

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
    const websocketAuthorizer = this.buildWebsocketAuthorizer(authorizerLambda)

    this.buildRoute('$connect', lambdaIntegration, websocketAuthorizer)
    this.buildRoute('$default', lambdaIntegration)
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

    const integrationResponseLocalId = 'graphql-handler-integration-response'
    const integrationResponse = new CfnIntegrationResponse(this.stack, integrationResponseLocalId, {
      integrationId: integration.ref,
      apiId: this.apis.websocketAPI.ref,
      integrationResponseKey: '$default',
    })
    integrationResponse.addDependsOn(integration)
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
    const table = new Table(this.stack, this.config.resourceNames.subscriptionsStore, {
      tableName: this.config.resourceNames.subscriptionsStore,
      partitionKey: {
        name: subscriptionsStoreAttributes.partitionKey,
        type: AttributeType.STRING,
      },
      sortKey: {
        name: subscriptionsStoreAttributes.sortKey,
        type: AttributeType.STRING,
      },
      billingMode: BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
      timeToLiveAttribute: subscriptionsStoreAttributes.ttl,
    })
    table.addGlobalSecondaryIndex({
      indexName: subscriptionsStoreAttributes.indexByConnectionIDName(this.config),
      partitionKey: {
        name: subscriptionsStoreAttributes.indexByConnectionIDPartitionKey,
        type: AttributeType.STRING,
      },
      sortKey: {
        name: subscriptionsStoreAttributes.indexByConnectionIDSortKey,
        type: AttributeType.STRING,
      },
      projectionType: ProjectionType.KEYS_ONLY,
    })
    return table
  }
}
