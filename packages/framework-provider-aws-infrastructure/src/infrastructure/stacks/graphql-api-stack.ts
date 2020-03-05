import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import {
  CfnApi,
  CfnIntegration,
  CfnRouteResponse,
  CfnRoute,
  CfnStage,
  CfnAuthorizer,
} from '@aws-cdk/aws-apigatewayv2'
import { Code, Function } from '@aws-cdk/aws-lambda'
import { Fn } from '@aws-cdk/core'
import * as params from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'

// - Headers are only sent in the connect request -> Store user someplace?
//   You need to connect and then subscribe -> Store connecitonID, accessToken (or user data?), GraphQL subscription(name)
// - When disconnect, remove this
// - What happens when the token expires? How to handle authentication with websockets?

interface GraphQLAPIStackMembers {
  graphQLLambda: Function
}

export class GraphQLAPIStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): GraphQLAPIStackMembers {
    const rootAPI = this.buildRootAPI()

    const [graphQLLambda, integration] = this.buildLambdaIntegration(rootAPI)
    const authorizer = this.buildAuthorizer(rootAPI)

    this.buildRoute('$connect', rootAPI, integration, authorizer)
    this.buildRoute('$disconnect', rootAPI, integration)
    const defaultRoute = this.buildRoute('$default', rootAPI, integration)
    this.buildRouteResponse(defaultRoute, rootAPI)

    return { graphQLLambda }
  }
  private buildRootAPI(): CfnApi {
    const localID = this.config.resourceNames.applicationStack + '-graphql-api'
    const rootAPI = new CfnApi(this.stack, localID, {
      name: localID,
      protocolType: 'WEBSOCKET',
      routeSelectionExpression: '$request.body.action',
    })

    const stage = new CfnStage(this.stack, localID + '-stage', {
      apiId: rootAPI.ref,
      autoDeploy: true,
      stageName: 'dev',
    })
    stage.addDependsOn(rootAPI)
    return rootAPI
  }

  private buildLambdaIntegration(rootAPI: CfnApi): [Function, CfnIntegration] {
    const lambdaLocalID = this.config.resourceNames.applicationStack + '-graphql-handler'
    const graphQlLambda = new Function(this.stack, lambdaLocalID, {
      ...params.lambda,
      functionName: lambdaLocalID,
      handler: this.config.serveGraphQLHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })
    graphQlLambda.addPermission(lambdaLocalID + '-invocation-permission', {
      principal: new ServicePrincipal('apigateway.amazonaws.com'),
    })

    const integration = this.buildIntegration(rootAPI, graphQlLambda)
    return [graphQlLambda, integration]
  }

  private buildIntegration(rootAPI: CfnApi, lambda: Function): CfnIntegration {
    const localId = this.config.resourceNames.applicationStack + '-graphql-integration'
    const integration = new CfnIntegration(this.stack, localId, {
      apiId: rootAPI.ref,
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
    integration.addDependsOn(rootAPI)
    return integration
  }

  private buildRoute(routeKey: string, rootAPI: CfnApi, integration: CfnIntegration, authorizer?: CfnAuthorizer): CfnRoute {
    const localID = `${this.config.resourceNames.applicationStack}-route-${routeKey}`
    const route = new CfnRoute(this.stack, localID,{
      apiId: rootAPI.ref,
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

  private buildRouteResponse(route: CfnRoute, rootAPI: CfnApi): void {
    const localID = `${this.config.resourceNames.applicationStack}-route-${route}-response`
    const routeResponse = new CfnRouteResponse(this.stack, localID, {
      apiId: rootAPI.ref,
      routeId: route.ref,
      routeResponseKey: '$default',
    })
    routeResponse.addDependsOn(route)
  }

  private buildAuthorizer(rootAPI: CfnApi): CfnAuthorizer {
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
      apiId: rootAPI.ref,
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
