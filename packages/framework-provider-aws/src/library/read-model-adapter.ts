/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import { APIGatewayProxyEvent } from 'aws-lambda'
import {
  BoosterConfig,
  Logger,
  ReadModelInterface,
  ReadModelRequestEnvelope,
  UUID,
  InvalidParameterError,
} from '@boostercloud/framework-types'
import { CognitoIdentityServiceProvider, DynamoDB } from 'aws-sdk'
import { fetchUserFromRequest } from './user-envelopes'

export async function rawReadModelRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent
): Promise<ReadModelRequestEnvelope> {
  if (!request.pathParameters) {
    throw new InvalidParameterError('Could not find path parameters in URL')
  }
  const readModelName: string | undefined = request.pathParameters['readModelName']
  if (!readModelName) {
    throw new InvalidParameterError('No read model name provided')
  }

  return {
    requestID: request.requestContext.requestId,
    typeName: readModelName,
    version: 1, // TODO: How to manage versions in read model requests? They are the other way around.
    readModelID: request.pathParameters['id'],
    currentUser: await fetchUserFromRequest(request, userPool),
  }
}

/** @deprecated */
export async function fetchAllReadModels(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string
): Promise<Array<ReadModelInterface>> {
  const params = {
    TableName: config.resourceNames.forReadModel(readModelName),
  }
  const response = await db.scan(params).promise()
  logger.debug(`[ReadModelAdapter#fetchAllReadModels] Loaded ${readModelName} read models with result:`, response.Items)
  return response.Items as Array<ReadModelInterface>
}

/** @deprecated */
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
