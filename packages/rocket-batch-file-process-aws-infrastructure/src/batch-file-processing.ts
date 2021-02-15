import { Duration, Stack, RemovalPolicy } from '@aws-cdk/core'
import { RocketUtils } from '@boostercloud/framework-provider-aws-infrastructure'
import { Bucket, EventType } from '@aws-cdk/aws-s3'
import { Function, Runtime, Code } from '@aws-cdk/aws-lambda'
import { S3EventSource } from '@aws-cdk/aws-lambda-event-sources'
import { createPolicyStatement } from '@boostercloud/framework-provider-aws-infrastructure/dist/infrastructure/stacks/policies'
import { Table } from '@aws-cdk/aws-dynamodb'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'

export type AWSBatchProcessingFilesParams = {
  bucketName: string
  chunkSize: string
}

export class BatchFileProcessingStack {
  public static mountStack(params: AWSBatchProcessingFilesParams, stack: Stack, config: BoosterConfig): void {
    const sourceBucket = new Bucket(stack, 'sourceUploadBucket', {
      bucketName: params.bucketName,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    const stagingBucketName = params.bucketName + '-staging'

    const stagingBucket = new Bucket(stack, 'stagingUploadBucket', {
      bucketName: stagingBucketName,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    const eventsStore = stack.node.tryFindChild('events-store') as Table

    const fileTriggerFunction = new Function(stack, 'rocketS3Trigger', {
      runtime: Runtime.NODEJS_12_X,
      timeout: Duration.minutes(15),
      memorySize: 1024,
      handler: 'index.handler',
      functionName: config.appName + '-s3-rocket-trigger',
      code: Code.fromAsset(path.join(__dirname, 'lambdas')),
      environment: {
        EVENT_STORE_NAME: eventsStore.tableName,
        CHUNK_SIZE: params.chunkSize,
        STAGING_BUCKET_NAME: stagingBucketName,
        ENTITY_TYPE_NAME: 'File',
        TYPE_NAME: 'FileAdded',
      },
    })

    fileTriggerFunction.addToRolePolicy(createPolicyStatement([eventsStore.tableArn], ['dynamodb:Put*']))

    sourceBucket.grantRead(fileTriggerFunction)

    fileTriggerFunction.addToRolePolicy(
      createPolicyStatement(
        [stagingBucket.bucketArn, stagingBucket.bucketArn + '/*'],
        ['s3:ListObject', 's3:PutObject', 's3:GetObject']
      )
    )

    const uploadEvent = new S3EventSource(sourceBucket, {
      events: [EventType.OBJECT_CREATED],
    })
    fileTriggerFunction.addEventSource(uploadEvent)

    const eventsHandlerLambda = stack.node.tryFindChild('events-main') as Function
    eventsHandlerLambda.addToRolePolicy(
      createPolicyStatement([stagingBucket.bucketArn, stagingBucket.bucketArn + '/*'], ['s3:*'])
    )
  }

  public static async unmountStack(params: AWSBatchProcessingFilesParams, utils: RocketUtils): Promise<void> {
    // The bucket must be empty for the stack deletion to succeed
    await utils.s3.emptyBucket(params.bucketName)
    await utils.s3.emptyBucket(params.bucketName + '-staging')
  }
}
