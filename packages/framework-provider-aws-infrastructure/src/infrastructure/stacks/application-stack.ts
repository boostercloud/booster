import { App, Stack, StackProps } from '@aws-cdk/core'
import * as dynamodb from '@aws-cdk/aws-dynamodb'
import { Function } from '@aws-cdk/aws-lambda'
import { Stream } from '@aws-cdk/aws-kinesis'
import { BoosterConfig } from '@boostercloud/framework-types'
import { PolicyStatement } from '@aws-cdk/aws-iam'
import { ApiStack } from './api-stack'
import { AuthStack } from './auth-stack'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig, readonly props?: StackProps) {}

  public buildOn(app: App): void {
    const stack = new Stack(app, this.config.resourceNames.applicationStack, this.props)

    new AuthStack(this.config, stack).build()
    const readModelTables = new ReadModelsStack(this.config, stack).build()
    const apiStack = new ApiStack(this.config, stack).build()
    const eventsStack = new EventsStack(this.config, stack).build()

    setupPermissions(
      readModelTables,
      apiStack.commandsLambda,
      eventsStack.eventsLambda,
      apiStack.readModelFetcherLambda,
      apiStack.graphQLLambda,
      eventsStack.eventsStream,
      eventsStack.eventsStore,
      eventsStack.eventsLambda
    )
  }
}

function setupPermissions(
  readModelTables: Array<dynamodb.Table>,
  commandsLambda: Function,
  readModelsLambda: Function,
  readModelFetcherLambda: Function,
  graphQLLambda: Function,
  eventsStream: Stream,
  eventsStore: dynamodb.Table,
  eventsLambda: Function
): void {
  // The command dispatcher can send events to the event stream
  commandsLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStream.streamArn],
      actions: ['kinesis:Put*', 'dynamodb:Query*', 'dynamodb:Put*'],
    })
  )

  readModelsLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStore.tableArn],
      actions: ['dynamodb:Query*', 'dynamodb:Put*'],
    })
  )

  graphQLLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStore.tableArn],
      actions: ['dynamodb:Query*', 'dynamodb:Get*'],
    })
  )

  const tableArns = readModelTables.map((table): string => table.tableArn)
  if (tableArns.length > 0) {
    readModelFetcherLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Get*', 'dynamodb:Scan*'],
        resources: tableArns,
      })
    )
    eventsLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Get*', 'dynamodb:Put*'],
        resources: tableArns,
      })
    )
    readModelsLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Put*'],
        resources: tableArns,
      })
    )
  }
}
