import { CosmosClient, RequestOptions } from '@azure/cosmos'
import {
  BoosterConfig,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  UUID,
} from '@boostercloud/framework-types'
import { AZURE_CONFLICT_ERROR_CODE, AZURE_PRECONDITION_FAILED_ERROR } from '../constants'

export async function fetchReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  const { resource } = await db
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .item(readModelID as string, readModelID)
    .read()

  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    resource
  )

  if (!resource) return [resource as ReadModelInterface]

  return [
    {
      ...resource,
      boosterMetadata: {
        ...resource?.boosterMetadata,
        optimisticConcurrencyValue: resource._etag,
      },
    } as ReadModelInterface,
  ]
}

async function updateReadModel(
  readModel: ReadModelInterface,
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string,
  logger: Logger
): Promise<void> {
  /** upsert only occurs if the etag we are sending matches the etag on the server. i.e. Only replace if the item hasn't changed */
  const options = {
    accessCondition: { condition: readModel.boosterMetadata?.optimisticConcurrencyValue || '*', type: 'IfMatch' },
  } as RequestOptions
  try {
    await db
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.forReadModel(readModelName))
      .items.upsert(readModel, options)
    logger.debug('[ReadModelAdapter#updateReadModel] Read model updated')
  } catch (err) {
    // If there is a precondition failure then we should retry it
    if (err?.code === AZURE_PRECONDITION_FAILED_ERROR || err?.code === AZURE_CONFLICT_ERROR_CODE) {
      logger.debug('[ReadModelAdapter#updateReadModel] Read model update failed with a pre-condition failure')
      throw new OptimisticConcurrencyUnexpectedVersionError(err?.message)
    }
    logger.error('[ReadModelAdapter#updateReadModel] Read model update failed without a pre-condition failure')
    throw err
  }
}

export async function storeReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  await updateReadModel(readModel, db, config, readModelName, logger)
}

export async function deleteReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  logger.debug(`[ReadModelAdapter#deleteReadModel] Entering to Read model deleted. ID = ${readModel.id}`)
  await db
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .item(readModel.id as string, readModel.id)
    .delete()
  logger.debug(`[ReadModelAdapter#deleteReadModel] Read model deleted. ID = ${readModel.id}`)
}
