import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import { CfnApi, CfnIntegration, CfnIntegrationResponse, CfnRoute, CfnStage } from '@aws-cdk/aws-apigatewayv2'
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

    this.buildRoute('$connect', rootAPI, integration)
    this.buildRoute('$disconnect', rootAPI, integration)
    this.buildRoute('$default', rootAPI, integration)

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
    const requestIntegrationLocalId = this.config.resourceNames.applicationStack + '-graphql-integration'
    const requestIntegration = new CfnIntegration(this.stack, requestIntegrationLocalId, {
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
    requestIntegration.addDependsOn(rootAPI)

    const responseIntegrationLocalID = this.config.resourceNames.applicationStack + '-graphql-integration-response'
    const responseIntegration = new CfnIntegrationResponse(this.stack, responseIntegrationLocalID, {
      apiId: rootAPI.ref,
      integrationId: requestIntegration.ref,
      integrationResponseKey: '$default',
    })
    responseIntegration.addDependsOn(requestIntegration)
    return requestIntegration
  }

  private buildRoute(route: string, rootAPI: CfnApi, integration: CfnIntegration): void {
    const localID = `${this.config.resourceNames.applicationStack}-route-${route}`
    const connectRoute = new CfnRoute(this.stack, localID, {
      apiId: rootAPI.ref,
      routeKey: route,
      target: Fn.join('/', ['integrations', integration.ref]),
    })
    connectRoute.addDependsOn(integration)
  }
}
