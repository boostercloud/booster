/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { BoosterConfig, ReadModelInterface, UUID, Logger } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { fail, succeed } from './api-gateway-io'

export async function processReadModelAPICall(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  event: APIGatewayProxyEvent
): Promise<APIGatewayProxyResult> {
  if (!event.pathParameters) {
    return noPathParametersProvided()
  }

  const readModelName: string | undefined = event.pathParameters['readModelName']
  const requestedId: string | undefined = event.pathParameters['id']

  if (!readModelName) {
    return noReadModelProvided()
  }
  const readModelResource = config.resourceNames.forReadModel(readModelName)

  if (requestedId) {
    return requestOne(dynamoDB, readModelResource, requestedId)
  }
  return requestAll(dynamoDB, readModelResource)
}

function noReadModelProvided(): APIGatewayProxyResult {
  return fail(400, 'Request error', 'No read model name provided')
}

function noPathParametersProvided(): APIGatewayProxyResult {
  return fail(400, 'Request error', 'Could not find path parameters in URL')
}

async function requestAll(db: DynamoDB.DocumentClient, readModelResource: string): Promise<APIGatewayProxyResult> {
  const params = {
    TableName: readModelResource,
  }
  try {
    const response = await db.scan(params).promise()
    return succeed(response.Items)
  } catch (dbError) {
    return fail(503, 'Service unavailable', dbError.toString())
  }
}

async function requestOne(
  db: DynamoDB.DocumentClient,
  readModelResource: string,
  id: string
): Promise<APIGatewayProxyResult> {
  try {
    const params = {
      TableName: readModelResource,
      Key: { id },
    }
    const response = await db.get(params).promise()
    return succeed(response.Item)
  } catch (dbError) {
    return fail(503, 'Service unavailable', dbError.toString())
  }
}

export async function fetchReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID
): Promise<ReadModelInterface> {
  const params = {
    TableName: config.resourceNames.forReadModel(readModelName),
    Key: { id: readModelID },
  }
  const response = await db.get(params).promise()
  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    response.Item
  )
  return response.Item as ReadModelInterface
}

export async function storeReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  await db
    .put({
      TableName: config.resourceNames.forReadModel(readModelName),
      Item: readModel,
    })
    .promise()
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}
