import { DynamoDB, SQS } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { Booster } from '@boostercloud/framework-core'
import { errorResponse, okResponse } from './response'
import { RestoreMessage } from './types'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const dynamoDB = await new DynamoDB()
    const sqs = await new SQS()
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
    await restoreModels(tableNames, dynamoDB, sqs, pointInTimeISO)
    return okResponse({ restore_status: 'IN_PROGRESS' })
  } catch (e) {
    return errorResponse(e)
  }
}

const restoreModels = async (
  tableNames: Array<string>,
  dynamoDB: DynamoDB,
  sqs: SQS,
  pointInTimeISO?: string
): Promise<void> => {
  const pointInTimeDateTime = pointInTimeISO ? new Date(pointInTimeISO) : undefined
  for (let i = 0; i < tableNames.length; i++) {
    const tableName = tableNames[i]
    const newTableName = `${tableName}-restoring`

    try {
      // https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_RestoreTableToPointInTime.html
      // Some properties are lost during the restoration process.
      // We only need to replicate the DynamoDB stream for now.
      const tableDescribeData = await dynamoDB.describeTable({ TableName: tableName }).promise()
      const oldTableStreamSpecification = tableDescribeData.Table?.StreamSpecification!
      await dynamoDB
        .restoreTableToPointInTime({
          SourceTableName: tableName,
          TargetTableName: newTableName,
          UseLatestRestorableTime: !pointInTimeISO,
          RestoreDateTime: pointInTimeDateTime,
        })
        .promise()
      const message: RestoreMessage = {
        from_table: tableName,
        to_table: newTableName,
        status: 'RESTORING_TEMPORAL_TABLE',
        options: oldTableStreamSpecification
      }
      await sqs.sendMessage({ QueueUrl: process.env['SQS_URL']!, MessageBody: JSON.stringify(message) }).promise()
    } catch (e) {
      throw Error(`An error has occurred while restoring your model: ${e.message}`)
    }
  }
}
