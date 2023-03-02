import { CosmosClient, ItemDefinition, RequestOptions } from '@azure/cosmos'
import {
  BoosterConfig,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { AZURE_CONFLICT_ERROR_CODE, AZURE_PRECONDITION_FAILED_ERROR } from '../constants'

export async function fetchReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string,
  readModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  const logger = getLogger(config, 'read-model-adapter#fetchReadModel')
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

async function insertReadModel(
  readModel: ReadModelInterface,
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string
): Promise<void> {
  const logger = getLogger(config, 'read-model-adapter#insertReadModel')
  try {
    const itemModel = {
      ...readModel,
      id: readModel?.id?.toString(),
    } as ItemDefinition

    await db
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.forReadModel(readModelName))
      .items.create(itemModel)
    logger.debug('[ReadModelAdapter#insertReadModel] Read model inserted')
  } catch (err) {
    const error = err as Error & { code?: unknown }
    // In case of conflict (The ID provided for a resource on a PUT or POST operation has been taken by an existing resource) we should retry it
    if (error?.code == AZURE_CONFLICT_ERROR_CODE) {
      logger.debug('[ReadModelAdapter#insertReadModel] Read model insert failed with a conflict failure')
      throw new OptimisticConcurrencyUnexpectedVersionError(error?.message)
    }
    logger.error('[ReadModelAdapter#insertReadModel] Read model insert failed without a conflict failure')
    throw error
  }
}

async function updateReadModel(
  readModel: ReadModelInterface,
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string
): Promise<void> {
  const logger = getLogger(config, 'read-model-adapter#updateReadModel')
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
    const error = err as Error & { code?: unknown }
    // If there is a precondition failure then we should retry it
    if (error?.code == AZURE_PRECONDITION_FAILED_ERROR) {
      logger.debug('[ReadModelAdapter#updateReadModel] Read model update failed with a pre-condition failure')
      throw new OptimisticConcurrencyUnexpectedVersionError(error?.message)
    }
    logger.error('[ReadModelAdapter#updateReadModel] Read model update failed without a pre-condition failure')
    throw err
  }
}

export async function storeReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  const version = readModel.boosterMetadata?.version ?? 1
  if (version === 1) {
    return await insertReadModel(readModel, db, config, readModelName)
  }
  return await updateReadModel(readModel, db, config, readModelName)
}

export async function deleteReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  const logger = getLogger(config, 'read-model-adapter#deleteReadModel')
  logger.debug(`[ReadModelAdapter#deleteReadModel] Entering to Read model deleted. ID = ${readModel.id}`)
  await db
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .item(readModel.id as string, readModel.id)
    .delete()
  logger.debug(`[ReadModelAdapter#deleteReadModel] Read model deleted. ID = ${readModel.id}`)
}
