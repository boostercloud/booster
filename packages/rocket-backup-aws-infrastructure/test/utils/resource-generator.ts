import { Stack, RemovalPolicy } from '@aws-cdk/core'
import { AttributeType, Table, BillingMode } from '@aws-cdk/aws-dynamodb'
import { random } from 'faker'

export const generateDynamoDBTable = (appStack: Stack, id?: string, tableName?: string): Table => {
  return new Table(appStack, id ?? random.word(), {
    tableName: tableName ?? random.word(),
    partitionKey: {
      name: random.word(),
      type: AttributeType.STRING,
    },
    sortKey: {
      name: random.word(),
      type: AttributeType.STRING,
    },
    billingMode: BillingMode.PAY_PER_REQUEST,
    removalPolicy: RemovalPolicy.DESTROY,
    timeToLiveAttribute: random.word(),
    pointInTimeRecovery: false,
  })
}
