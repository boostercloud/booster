import * as lambda from '@aws-cdk/aws-lambda'
import { Duration, Stack } from '@aws-cdk/core'
import * as path from 'path'

export const createLambda = (
  stack: Stack,
  name: string,
  handler: string,
  environment?: Record<string, string>
): lambda.Function => {
  return new lambda.Function(stack, name, {
    runtime: lambda.Runtime.NODEJS_12_X,
    timeout: Duration.minutes(15),
    memorySize: 1024,
    handler: handler,
    functionName: name,
    code: lambda.Code.fromAsset(path.join(__dirname, 'lambdas')),
    environment,
  })
}
