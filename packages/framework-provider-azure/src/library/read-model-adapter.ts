import { CosmosClient, ItemDefinition, RequestOptions } from '@azure/cosmos'
import {
  BoosterConfig,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  UUID,
} from '@boostercloud/framework-types'

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

async function insertReadModel(
  logger: Logger,
  readModel: ReadModelInterface,
  db: CosmosClient,
  config: BoosterConfig,
  readModelName: string
): Promise<void> {
  const itemModel = {
    ...readModel,
    id: readModel.id.toString(),
  } as ItemDefinition
  try {
    await db
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.forReadModel(readModelName))
      .items.create(itemModel)
    logger.debug('[ReadModelAdapter#insertReadModel] Read model inserted')
  } catch (err) {
    // In case of conflict (The ID provided for a resource on a PUT or POST operation has been taken by an existing resource) we should retry it
    if (err.code == 409) {
      logger.debug('[ReadModelAdapter#insertReadModel] Read model insert failed with a conflict failure')
      throw new OptimisticConcurrencyUnexpectedVersionError(err.message)
    }
    throw err
  }
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
    if (err.code == 412) {
      logger.debug('[ReadModelAdapter#updateReadModel] Read model update failed with a pre-condition failure')
      throw new OptimisticConcurrencyUnexpectedVersionError(err.message)
    }
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
  const version = readModel.boosterMetadata?.version || 0
  if (version === 0) {
    await insertReadModel(logger, readModel, db, config, readModelName)
  }
  await updateReadModel(readModel, db, config, readModelName, logger)
}
