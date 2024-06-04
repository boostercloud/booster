import { CosmosClient, ItemDefinition, RequestOptions } from '@azure/cosmos'
import {
  BoosterConfig,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { AZURE_CONFLICT_ERROR_CODE, AZURE_PRECONDITION_FAILED_ERROR } from '../constants'
import { RawEvent, SubscriptionContext } from './subscription-model'

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
    .item(readModelID as string, readModelID as string)
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

    const { resource } = await db
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.forReadModel(readModelName))
      .items.create(itemModel)
    logger.debug(
      `Read model ${readModelName} inserted with id ${readModel.id} and metadata ${JSON.stringify(
        resource?.boosterMetadata
      )}`
    )
  } catch (err) {
    const error = err as Error & { code?: unknown }
    // In case of conflict (The ID provided for a resource on a PUT or POST operation has been taken by an existing resource) we should retry it
    if (error?.code == AZURE_CONFLICT_ERROR_CODE) {
      logger.warn(
        `Read model ${readModelName} insert failed with a conflict failure with id ${
          readModel.id
        } and metadata ${JSON.stringify(readModel.boosterMetadata)}`
      )
      throw new OptimisticConcurrencyUnexpectedVersionError(error?.message)
    }
    logger.error('[ReadModelAdapter#insertReadModel] Read model insert failed without a conflict failure', error)
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
    const { resource } = await db
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.forReadModel(readModelName))
      .items.upsert(readModel, options)
    logger.debug(
      `Read model ${readModelName} updated with id ${readModel.id} and metadata ${JSON.stringify(
        resource?.boosterMetadata
      )}`
    )
  } catch (err) {
    const error = err as Error & { code?: unknown }
    // If there is a precondition failure then we should retry it
    if (error?.code == AZURE_PRECONDITION_FAILED_ERROR) {
      logger.warn(
        `Read model ${readModelName} update failed with a pre-condition failure with id ${
          readModel.id
        } and metadata ${JSON.stringify(readModel.boosterMetadata)}`
      )
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
    .item(readModel.id as string, readModel.id as string)
    .delete()
  logger.debug(`[ReadModelAdapter#deleteReadModel] Read model deleted. ID = ${readModel.id}`)
}

export async function rawReadModelEventsToEnvelopes(
  config: BoosterConfig,
  rawEvents: unknown
): Promise<Array<ReadModelEnvelope>> {
  const logger = getLogger(config, 'read-model-adapter#rawReadModelEventsToEnvelopes')
  logger.debug(`Parsing raw read models ${JSON.stringify(rawEvents)}`)
  if (isSubscriptionContext(rawEvents)) {
    const typeName = rawEvents.executionContext.functionName.replace('-subscriptions-notifier', '')
    return rawEvents.bindings.rawEvent.map((rawEvent: RawEvent) => {
      const { _rid, _self, _st, _etag, _lsn, _ts, ...rest } = rawEvent
      return {
        typeName: typeName,
        value: rest as ReadModelInterface,
      }
    })
  }
  logger.warn(`Unexpected events to be parsed ${JSON.stringify(rawEvents)}`)
  return []
}

function isSubscriptionContext(rawRequest: unknown): rawRequest is SubscriptionContext {
  return (
    (rawRequest as SubscriptionContext).bindings !== undefined &&
    (rawRequest as SubscriptionContext).bindings.rawEvent !== undefined
  )
}
