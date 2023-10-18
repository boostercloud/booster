import { Code, Function as AWSFunction } from 'aws-cdk-lib/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack, RemovalPolicy } from 'aws-cdk-lib'
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb'
import { eventsStoreAttributes } from '@boostercloud/framework-provider-aws'
import * as params from '../params'
import { DynamoEventSource } from 'aws-cdk-lib/aws-lambda-event-sources'
import { APIs } from '../params'
import { Table, StreamViewType } from 'aws-cdk-lib/aws-dynamodb'

export interface EventsStackMembers {
  eventsStore: dynamodb.Table
  eventsLambda: AWSFunction
}

export class EventsStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs
  ) {}

  public build(): EventsStackMembers {
    const eventsStore = this.buildEventsStore()
    const eventsLambda = this.buildEventsLambda(eventsStore)
    return {
      eventsStore,
      eventsLambda,
    }
  }

  private buildEventsStore(): dynamodb.Table {
    const localID = 'events-store'
    const table = new dynamodb.Table(this.stack, localID, {
      tableName: this.config.resourceNames.eventsStore,
      partitionKey: {
        name: eventsStoreAttributes.partitionKey,
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: eventsStoreAttributes.sortKey,
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
      stream: StreamViewType.NEW_IMAGE,
    })

    table.addGlobalSecondaryIndex({
      indexName: eventsStoreAttributes.indexByEntity.name(this.config),
      partitionKey: {
        name: eventsStoreAttributes.indexByEntity.partitionKey,
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: eventsStoreAttributes.sortKey,
        type: dynamodb.AttributeType.STRING,
      },
      projectionType: dynamodb.ProjectionType.KEYS_ONLY,
    })

    table.addGlobalSecondaryIndex({
      indexName: eventsStoreAttributes.indexByType.name(this.config),
      partitionKey: {
        name: eventsStoreAttributes.indexByType.partitionKey,
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: eventsStoreAttributes.sortKey,
        type: dynamodb.AttributeType.STRING,
      },
      projectionType: dynamodb.ProjectionType.KEYS_ONLY,
    })
    return table
  }

  private buildEventsLambda(eventsStream: Table): AWSFunction {
    const localID = 'events-main'
    return new AWSFunction(this.stack, localID, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.eventDispatcherHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
      events: [new DynamoEventSource(eventsStream, params.stream())],
    })
  }
}
