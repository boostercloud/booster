import { DynamoDB } from 'aws-sdk'

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
    // TODO: This way of counting is wrong when there are a lot of events in the event store, as COUNT will only contain
    // the number of elements in a a result set that is lower than 1MB in size:
    // "If the size of the Query result set is larger than 1 MB, then ScannedCount and Count
    // will represent only a partial count of the total items.
    // You will need to perform multiple Query operations in order to retrieve all of the results\"."
    const output: DynamoDB.ScanOutput = await documentClient
      .scan({
        TableName: `${this.stackName}-events-store`,
        Select: 'COUNT',
        FilterExpression: '#k = :kind',
        ExpressionAttributeNames: { '#k': 'kind' },
        ExpressionAttributeValues: { ':kind': 'event' },
      })
      .promise()

    return output.Count ?? -1
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
