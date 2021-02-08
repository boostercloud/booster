import { DynamoDB } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { Booster } from '@boostercloud/framework-core'
import { TableDescription } from 'aws-sdk/clients/dynamodb'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const dynamoDB = await new DynamoDB()
    const allowedParams = ['model', 'pointInTimeISO']
    let tableNames
    const body = JSON.parse(event.body!)
    const pointInTimeISO = body?.pointInTimeISO
    const model = body?.model
    const unknownParamsProvided = body ? !Object.keys(body).every((p: string) => allowedParams.includes(p)) : false

    if (unknownParamsProvided) {
      throw Error(
        'Restore service has encountered unknown parameters. Valid parameters are: "model" and "pointInTimeISO"'
      )
    }

    if (model) {
      // On compile time, appName = 'new-booster-app'.
      Booster.config.appName = process.env['APP_NAME']!
      tableNames = new Array(Booster.config.resourceNames.forReadModel(model))
    } else {
      tableNames = process.env['TABLE_NAMES']!.split(',')
    }
    console.log('/////// TABLE NAMES ///////')
    console.log(tableNames)
    const response = await restoreModels(tableNames, dynamoDB, pointInTimeISO)
    return okResponse(response)
  } catch (e) {
    return errorResponse(e)
  }
}
const restoreModels = async (
  tableNames: Array<string>,
  dynamoDB: DynamoDB,
  pointInTimeISO?: string
): Promise<Array<TableDescription>> => {
  const response = [] as Array<TableDescription>
  const pointInTimeDateTime = pointInTimeISO ? new Date(pointInTimeISO) : undefined
  for (let i = 0; i < tableNames.length; i++) {
    const tableName = tableNames[i]
    const newTableName = `${tableName}-${pointInTimeDateTime ?? new Date().toLocaleString()}`

    try {
      // https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_RestoreTableToPointInTime.html
      // Some properties are lost during the restoration process:
      // Auto scaling policies, IAM policies, Amazon CloudWatch metrics and alarms, Tags, Stream settings,
      // Time to Live (TTL) settings and Point in time recovery settings
      const tableDescribeData = await dynamoDB.describeTable({ TableName: tableName }).promise()
      // Two tables:
      // 1. CartReadModel
      // 2. CartReadModel-Restoring: Common name so the GET endpoint can see the restoring process.
      // 2.1 Or CartReadModel-<pointInTimeISO | new Date()> - But we can't access through the GET method (maybe with regex)
      const tableRestoreData = await dynamoDB
        .restoreTableToPointInTime({
          SourceTableName: tableName,
          TargetTableName: newTableName,
          UseLatestRestorableTime: !pointInTimeISO,
          RestoreDateTime: pointInTimeDateTime,
        })
        .promise()
      response.push(tableRestoreData.TableDescription ?? {})
    } catch (e) {
      throw Error(`An error has occurred while restoring your model: ${e.message}`)
    }
  }
  return response
}
