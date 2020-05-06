/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import { BoosterConfig, Logger, ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'

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
