import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import { LambdaIntegration, RestApi } from '@aws-cdk/aws-apigateway'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'

interface RESTAPIStackMembers {
  /** @deprecated **/
  commandsLambda: Function
  /** @deprecated **/
  readModelFetcherLambda: Function
}

/**
 * @deprecated
 */
export class RestAPIStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly restAPI: RestApi
  ) {}

  public build(): RESTAPIStackMembers {
    const commandsLambda = this.buildCommandsAPI()
    const readModelFetcherLambda = this.buildReadModelsAPI()

    return {
      commandsLambda,
      readModelFetcherLambda,
    }
  }

  /**
   * @deprecated This will be passed to GraphQL
   * @param api
   */
  private buildCommandsAPI(): Function {
    const localID = 'commands-main'
    const lambdaFunction = new Function(this.stack, localID, {
      ...params.lambda,
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.commandDispatcherHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    this.restAPI.root.addResource('commands').addMethod('POST', new LambdaIntegration(lambdaFunction))
    return lambdaFunction
  }

  /**
   * @deprecated This will be passed to GraphQL
   * @param api
   */
  private buildReadModelsAPI(): Function {
    const localID = 'read-model-fetcher'
    const readModelFetcherLambda = new Function(this.stack, localID, {
      ...params.lambda,
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.readModelMapperHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    const lambdaIntegration = new LambdaIntegration(readModelFetcherLambda)
    const readModelResource = this.restAPI.root.resourceForPath('readmodels/{readModelName}')
    readModelResource.addMethod('GET', lambdaIntegration)
    readModelResource.addResource('{id}').addMethod('GET', lambdaIntegration)

    return readModelFetcherLambda
  }
}
