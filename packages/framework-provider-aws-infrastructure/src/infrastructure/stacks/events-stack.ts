import { Code, Function } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack, RemovalPolicy } from '@aws-cdk/core'
import { Stream } from '@aws-cdk/aws-kinesis'
import * as dynamodb from '@aws-cdk/aws-dynamodb'
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '@boostercloud/framework-provider-aws'
import * as params from '../params'
import { KinesisEventSource } from '@aws-cdk/aws-lambda-event-sources'

interface EventsStackMembers {
  eventsStream: Stream
  eventsStore: dynamodb.Table
  eventsLambda: Function
}

export class EventsStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): EventsStackMembers {
    const eventsStream = this.buildEventsStream()
    const eventsStore = this.buildEventsStore()
    const eventsLambda = this.buildEventsLambda(eventsStream)
    return {
      eventsStream,
      eventsStore,
      eventsLambda,
    }
  }

  private buildEventsStream(): Stream {
    const localID = 'events-stream'
    return new Stream(this.stack, localID, {
      streamName: this.config.resourceNames.eventsStream,
      shardCount: 1,
    })
  }

  private buildEventsStore(): dynamodb.Table {
    const localID = 'events-store'
    return new dynamodb.Table(this.stack, localID, {
      tableName: this.config.resourceNames.eventsStore,
      partitionKey: {
        name: eventStorePartitionKeyAttribute,
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: eventStoreSortKeyAttribute,
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
    })
  }

  private buildEventsLambda(eventsStream: Stream): Function {
    const localID = 'events-main'
    return new Function(this.stack, localID, {
      ...params.lambda(this.config),
      functionName: this.config.resourceNames.applicationStack + '-' + localID,
      handler: this.config.eventDispatcherHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
      events: [
        new KinesisEventSource(eventsStream, {
          ...params.stream(),
        }),
      ],
    })
  }
}
