import { Duration, RemovalPolicy, Stack } from '@aws-cdk/core'
import { Bucket, EventType } from '@aws-cdk/aws-s3'
import { RocketUtils } from '@boostercloud/framework-provider-aws-infrastructure'
import { Function, Runtime, Code } from '@aws-cdk/aws-lambda'
import { Table } from '@aws-cdk/aws-dynamodb'
import { S3EventSource } from '@aws-cdk/aws-lambda-event-sources'
import { createPolicyStatement } from '@boostercloud/framework-provider-aws-infrastructure/dist/infrastructure/stacks/policies'

const path = require('path')

export type AWSS3EventsLambdaParams = {
  bucketName: string
  eventTypeName: string
  entityTypeName: string
}

export class S3NotificationEventStoreStack {
  public static mountStack(params: AWSS3EventsLambdaParams, stack: Stack): void {
    const appName = stack.stackName.split('-app')[0]

    // Create new bucket to upload file
    const sourceBucket = new Bucket(stack, 'sourceUploadBucket' + params.entityTypeName, {
      bucketName: params.bucketName,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    const eventsStore = stack.node.tryFindChild('events-store') as Table

    // Crete new Lambda function to be triggered once new file is uploaded in bucket
    const processorFunction = new Function(stack, 'rocketS3Trigger' + params.entityTypeName, {
      runtime: Runtime.NODEJS_12_X,
      timeout: Duration.minutes(15),
      memorySize: 1024,
      handler: 'index.handler',
      functionName: appName + '-' + params.entityTypeName.toLowerCase() +'-s3-rocket-trigger',
      code: Code.fromAsset(path.join(__dirname, 'lambdas')),
      environment: {
        EVENT_STORE_NAME:eventsStore.tableName,
        ENTITY_TYPE_NAME: params.entityTypeName,
        TYPE_NAME: params.eventTypeName,
      },
    })

    // Grant access for this lambda to persist new records in the Events Store
    processorFunction.addToRolePolicy(
      createPolicyStatement([eventsStore.tableArn], ['dynamodb:Put*'])
    )
    // Grant access for this lambda to read from the S3 bucket
    sourceBucket.grantRead(processorFunction)

    // Create trigger from S3 to Lambda
    const uploadEvent = new S3EventSource(sourceBucket, {
      events: [EventType.OBJECT_CREATED],
    })
    processorFunction.addEventSource(uploadEvent)

    // Grant the Events handler lambda access to read from S3 bucket
    const eventsHandlerLambda = stack.node.tryFindChild('events-main') as Function
    eventsHandlerLambda.addToRolePolicy(
      createPolicyStatement([sourceBucket.bucketArn, sourceBucket.bucketArn + '/*'], ['s3:*'])
    )
  }

  public static async unmountStack(params: AWSS3EventsLambdaParams, utils: RocketUtils): Promise<void> {
    // The bucket must be empty for the stack deletion to succeed
    await utils.s3.emptyBucket(params.bucketName)
  }
}
