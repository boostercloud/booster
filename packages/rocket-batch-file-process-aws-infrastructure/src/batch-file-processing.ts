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
  config: {
    bucketName: string
    chunkSize: string
  }
  rowEvent: {
    entityId: string
    eventTypeName: string
    entityTypeName: string
  }
}

export class BatchFileProcessingStack {
  public static mountStack(params: AWSBatchProcessingFilesParams, stack: Stack, config: BoosterConfig): void {
    const sourceBucket = new Bucket(stack, 'sourceUploadBucket', {
      bucketName: params.config.bucketName,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    const stagingBucketName = params.config.bucketName + '-staging'

    const stagingBucket = new Bucket(stack, 'stagingUploadBucket', {
      bucketName: stagingBucketName,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    const eventsStore = stack.node.tryFindChild('events-store') as Table

    const fileSplitterFunction = new Function(stack, 'fileSplitterFunction', {
      runtime: Runtime.NODEJS_12_X,
      timeout: Duration.minutes(15),
      memorySize: 1024,
      handler: 'index.handler',
      functionName: config.appName + '-file-splitter-function',
      code: Code.fromAsset(path.join(__dirname, 'file-splitter-lambda')),
      environment: {
        EVENT_STORE_NAME: eventsStore.tableName,
        CHUNK_SIZE: params.config.chunkSize,
        STAGING_BUCKET_NAME: stagingBucketName,
        ENTITY_TYPE_NAME: 'File',
        TYPE_NAME: 'FileAdded',
      },
    })

    fileSplitterFunction.addToRolePolicy(createPolicyStatement([eventsStore.tableArn], ['dynamodb:Put*']))

    sourceBucket.grantRead(fileSplitterFunction)

    fileSplitterFunction.addToRolePolicy(
      createPolicyStatement(
        [stagingBucket.bucketArn, stagingBucket.bucketArn + '/*'],
        ['s3:ListObject', 's3:PutObject', 's3:GetObject']
      )
    )

    const sourceUploadEvent = new S3EventSource(sourceBucket, {
      events: [EventType.OBJECT_CREATED],
    })
    fileSplitterFunction.addEventSource(sourceUploadEvent)

    const eventsHandlerLambda = stack.node.tryFindChild('events-main') as Function
    eventsHandlerLambda.addToRolePolicy(
      createPolicyStatement([stagingBucket.bucketArn, stagingBucket.bucketArn + '/*'], ['s3:*'])
    )

    const fileToLineEventFunction = new Function(stack, 'fileToLineEventFunction', {
      runtime: Runtime.NODEJS_12_X,
      timeout: Duration.minutes(15),
      memorySize: 1024,
      handler: 'index.handler',
      functionName: config.appName + '-file-to-line-event-function',
      code: Code.fromAsset(path.join(__dirname, 'file-to-line-event-lambda')),
      environment: {
        EVENT_STORE_NAME: eventsStore.tableName,
        ENTITY_ID: params.rowEvent.entityId,
        ENTITY_TYPE_NAME: params.rowEvent.entityTypeName,
        TYPE_NAME: params.rowEvent.eventTypeName,
      },
    })

    const stagingUploadEvent = new S3EventSource(stagingBucket, {
      events: [EventType.OBJECT_CREATED],
    })
    fileToLineEventFunction.addEventSource(stagingUploadEvent)

    stagingBucket.grantRead(fileToLineEventFunction)

    fileToLineEventFunction.addToRolePolicy(createPolicyStatement([eventsStore.tableArn], ['dynamodb:Put*']))
  }

  public static async unmountStack(params: AWSBatchProcessingFilesParams, utils: RocketUtils): Promise<void> {
    // The bucket must be empty for the stack deletion to succeed
    await utils.s3.emptyBucket(params.config.bucketName)
    await utils.s3.emptyBucket(params.config.bucketName + '-staging')
  }
}
