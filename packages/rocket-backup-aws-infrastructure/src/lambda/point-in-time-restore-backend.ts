import { DynamoDB } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { Booster } from '@boostercloud/framework-core'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const dynamoDB = await new DynamoDB()
    const allowedParams = ['model', 'pointInTimeISO']
    let tableNames
    const body = JSON.parse(event.body!)
    console.log(body)
    const pointInTimeISO = body.pointInTimeISO
    const model = body.model

    const unknownParamsProvided = !Object.keys(body).every((p: string) => allowedParams.includes(p))
    if (unknownParamsProvided) {
      throw Error(
        'Restore service has encountered unknown parameters. Valid parameters are: "model" and "pointInTimeISO"'
      )
    }
    // If 'model' is not specified, then call restoreModels with all tables
    // If 'model' is specified, transform it into an array with its tableName and then call restoreModels
    // If request has unknown params, throw an error
    if (model) {
      tableNames = new Array(Booster.config.resourceNames.forReadModel(model))
    } else {
      tableNames = process.env['TABLE_NAMES']!.split(',')
    }
    const response = await restoreModels(tableNames, dynamoDB, pointInTimeISO)
    return okResponse(response)
  } catch (e) {
    return errorResponse(e)
  }
}
const restoreModels = async (tableNames: Array<string>, dynamoDB: DynamoDB, pointInTimeISO?: string) => {
  const response = []
  for (let i = 0; i < tableNames.length; i++) {
    const tableName = tableNames[i]
    // Will this work???
    try {
      const tableDescription = await dynamoDB
        .restoreTableToPointInTime({
          SourceTableName: tableName,
          TargetTableName: tableName,
          RestoreDateTime: pointInTimeISO ? new Date(pointInTimeISO) : undefined,
        })
        .promise()
      response.push(tableDescription.TableDescription)
    } catch (e) {
      throw Error(`An error has occurred while restoring your model - ${e.message}`)
    }
  }
  return response
}
