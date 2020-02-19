/* eslint-disable @typescript-eslint/no-magic-numbers */
import { Duration } from '@aws-cdk/core'
import { Runtime, StartingPosition } from '@aws-cdk/aws-lambda'

export const lambda = {
  runtime: Runtime.NODEJS_12_X,
  timeout: Duration.minutes(1),
  memorySize: 1024,
}

export const kinesis = {
  startingPosition: StartingPosition.TRIM_HORIZON,
  batchSize: 100,
}
