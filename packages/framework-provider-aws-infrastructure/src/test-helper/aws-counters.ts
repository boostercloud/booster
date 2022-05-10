import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'

const documentClient = new DynamoDB.DocumentClient()

export class AWSCounters {
  constructor(private readonly stackName: string) {}

  public async subscriptions(): Promise<number> {
    return this.countTableItems(`${this.stackName}-subscriptions-store`)
  }

  public async connections(): Promise<number> {
    return this.countTableItems(`${this.stackName}-connections-store`)
  }

  public async events(): Promise<number> {
    let scanParams: DocumentClient.ScanInput = {
      TableName: `${this.stackName}-events-store`,
      Select: 'COUNT',
      FilterExpression: '#k = :kind',
      ExpressionAttributeNames: { '#k': 'kind' },
      ExpressionAttributeValues: { ':kind': 'event' },
      ConsistentRead: true,
    }

    let count = 0
    let output

    do {
      output = await documentClient.scan(scanParams).promise()
      if (output.Count) {
        count += output.Count
        scanParams = { ...scanParams, ExclusiveStartKey: output.LastEvaluatedKey }
      } else {
        throw Error('Unable to find a count value for the events scan')
      }
    } while (typeof output.LastEvaluatedKey !== 'undefined')

    return count
  }

  public async readModels(readModelName: string): Promise<number> {
    return this.countTableItems(`${this.stackName}-${readModelName}`)
  }

  private async countTableItems(tableName: string): Promise<number> {
    const output: DynamoDB.ScanOutput = await documentClient
      .scan({
        TableName: tableName,
        Select: 'COUNT',
      })
      .promise()

    return output.Count ?? -1
  }
}
