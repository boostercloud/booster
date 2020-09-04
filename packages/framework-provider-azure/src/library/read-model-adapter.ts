import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, Logger, ReadModelInterface, UUID } from '@boostercloud/framework-types'

export async function fetchReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID
): Promise<ReadModelInterface> {
  const { resource } = await db
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .item(readModelID as string, readModelID)
    .read()

  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    resource
  )
  return resource as ReadModelInterface
}

export async function storeReadModel(
  db: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  await db
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .items.upsert(readModel)
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}
