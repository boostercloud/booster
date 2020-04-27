import { BoosterConfig } from '@boostercloud/framework-types'
import { RemovalPolicy, Stack } from '@aws-cdk/core'
import * as dynamodb from '@aws-cdk/aws-dynamodb'
import { StreamViewType } from '@aws-cdk/aws-dynamodb'

export class ReadModelsStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): Array<dynamodb.Table> {
    if (!this.config.readModels) {
      return []
    }
    return Object.keys(this.config.readModels).map((readModelName) => {
      return new dynamodb.Table(this.stack, readModelName, {
        tableName: this.config.resourceNames.forReadModel(readModelName),
        partitionKey: {
          name: 'id',
          type: dynamodb.AttributeType.STRING,
        },
        billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
        removalPolicy: RemovalPolicy.DESTROY,
        stream: StreamViewType.NEW_IMAGE,
      })
    })
  }
}
