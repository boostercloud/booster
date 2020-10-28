/* eslint-disable @typescript-eslint/no-magic-numbers */
import { Duration, Stack } from '@aws-cdk/core'
import { FunctionProps, Runtime, StartingPosition } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { environmentVarNames } from '@boostercloud/framework-provider-aws'
import { DynamoEventSourceProps } from '@aws-cdk/aws-lambda-event-sources'
import { UserPool } from '@aws-cdk/aws-cognito'

export interface APIs {
  restAPI: RestApi
  websocketAPI: CfnApi
}

export function lambda(
  config: BoosterConfig,
  stack: Stack,
  apis: APIs,
  userPool?: UserPool
): Pick<FunctionProps, 'runtime' | 'timeout' | 'memorySize' | 'environment'> {
  return {
    runtime: Runtime.NODEJS_12_X,
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      BOOSTER_ENV: config.environmentName,
      ...config.env, // Adds custom environment variables set by the user in the config file
      [environmentVarNames.restAPIURL]: baseURLForAPI(config, stack, apis.restAPI.restApiId),
      [environmentVarNames.websocketAPIURL]: baseURLForAPI(config, stack, apis.websocketAPI.ref),
      [environmentVarNames.jwtIssuer]: userPool ? issuer(stack, userPool?.userPoolId) : '',
      [environmentVarNames.jwksUri]: userPool ? jwksUri(stack, userPool?.userPoolId) : '',
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

export function stream(): Pick<DynamoEventSourceProps, 'startingPosition' | 'batchSize'> {
  return {
    startingPosition: StartingPosition.TRIM_HORIZON,
    batchSize: 100,
  }
}

export function issuer(stack: Stack, userPoolId: string): string {
  return `https://cognito-idp.${stack.region}.${stack.urlSuffix}/${userPoolId}`
}

export function jwksUri(stack: Stack, userPoolId: string): string {
  return issuer(stack, userPoolId) + '/.well-known/jwks.json'
}
