//import { DynamoDB } from 'aws-sdk'

//const documentClient = new DynamoDB.DocumentClient()

export class LocalQueries {
  constructor() {}

  public async events(primaryKey: string, latestFirst = true): Promise<Array<unknown>> {
    // const output: DynamoDB.QueryOutput = await documentClient
    //   .query({
    //     TableName: `${this.stackName}-events-store`,
    //     ConsistentRead: true,
    //     KeyConditionExpression: 'entityTypeName_entityID_kind = :v',
    //     ExpressionAttributeValues: { ':v': primaryKey },
    //     ScanIndexForward: !latestFirst,
    //   })
    //   .promise()

    // return output.Items || []
    return []
  }

  public async readModels(primaryKey: string, readModelName: string, latestFirst = true): Promise<Array<unknown>> {
    // const output: DynamoDB.QueryOutput = await documentClient
    //   .query({
    //     TableName: `${this.stackName}-${readModelName}`,
    //     KeyConditionExpression: 'id = :v',
    //     ExpressionAttributeValues: { ':v': primaryKey },
    //     ScanIndexForward: !latestFirst,
    //   })
    //   .promise()

    // return output.Items || []
    return []
  }
}