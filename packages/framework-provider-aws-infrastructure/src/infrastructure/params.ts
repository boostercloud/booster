/* eslint-disable @typescript-eslint/no-magic-numbers */
import { Duration, Stack } from '@aws-cdk/core'
import { FunctionProps, Runtime, StartingPosition } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { environmentVarNames } from '@boostercloud/framework-provider-aws'
import { DynamoEventSourceProps } from '@aws-cdk/aws-lambda-event-sources'

export interface APIs {
  restAPI: RestApi
  websocketAPI?: CfnApi
}

export function lambda(
  config: BoosterConfig,
  stack: Stack,
  apis: APIs
): Pick<FunctionProps, 'runtime' | 'timeout' | 'memorySize' | 'environment'> {
  return {
    runtime: Runtime.NODEJS_14_X,
    timeout: Duration.minutes(15),
    memorySize: 1024,
    environment: {
      BOOSTER_ENV: config.environmentName,
      ...config.env, // Adds custom environment variables set by the user in the config file
      [environmentVarNames.restAPIURL]: baseURLForAPI(config, stack, apis.restAPI.restApiId),
      [environmentVarNames.websocketAPIURL]: apis.websocketAPI
        ? baseURLForAPI(config, stack, apis.websocketAPI.ref)
        : '',
    },
  }
}

export function baseURLForAPI(
  config: BoosterConfig,
  stack: Stack,
  apiID: string,
  protocol: 'https' | 'wss' = 'https'
): string {
  return `${protocol}://${apiID}.execute-api.${stack.region}.${stack.urlSuffix}/${config.environmentName}/`
}

export function stream(): DynamoEventSourceProps {
  return {
    startingPosition: StartingPosition.TRIM_HORIZON,
    batchSize: 1000,
    parallelizationFactor: 10,
  }
}
