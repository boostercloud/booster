/* eslint-disable @typescript-eslint/no-magic-numbers */
import { Duration } from '@aws-cdk/core'
import { FunctionProps, Runtime, StartingPosition } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { KinesisEventSourceProps } from '@aws-cdk/aws-lambda-event-sources/lib/kinesis'

export function lambda(
  config: BoosterConfig
): Pick<FunctionProps, 'runtime' | 'timeout' | 'memorySize' | 'environment'> {
  return {
    runtime: Runtime.NODEJS_12_X,
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      BOOSTER_ENV: config.environment,
      ...config.env // Adds custom environment variables set by the user in the config file
    },
  }
}

export function kinesis(): Pick<KinesisEventSourceProps, 'startingPosition' | 'batchSize'> {
  return {
    startingPosition: StartingPosition.TRIM_HORIZON,
    batchSize: 100,
  }
}
