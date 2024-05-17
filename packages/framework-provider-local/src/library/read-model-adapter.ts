import {
  BoosterConfig,
  FilterFor,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionFor,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadModelListResult,
  ReadOnlyNonEmptyArray,
  SortFor,
  UUID,
} from '@boostercloud/framework-types'
import { GraphQLService, NedbError, ReadModelRegistry, UNIQUE_VIOLATED_ERROR_TYPE } from '../services'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { queryRecordFor } from './searcher-adapter'

export async function rawReadModelEventsToEnvelopes(
  config: BoosterConfig,
  rawEvents: Array<unknown>
): Promise<Array<ReadModelEnvelope>> {
  return rawEvents as Array<ReadModelEnvelope>
}

export async function fetchReadModel(
  db: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  const logger = getLogger(config, 'read-model-adapter#fetchReadModel')
  //use dot notation value.id to match the record (see https://github.com/louischatriot/nedb#finding-documents)
  const response = await db.query({ typeName: readModelName, 'value.id': readModelID })
  const item = response[0]
  if (!item) {
    logger.debug(`Read model ${readModelName} with ID ${readModelID} not found`)
  } else {
    logger.debug(`Loaded read model ${readModelName} with ID ${readModelID} with result:`, item.value)
  }
  return [item?.value]
}

export async function storeReadModel(
  graphQLService: GraphQLService,
  db: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface,
  expectedCurrentVersion: number
): Promise<void> {
  const logger = getLogger(config, 'read-model-adapter#storeReadModel')
  logger.debug('Storing readModel ' + JSON.stringify(readModel))
  try {
    await db.store({ typeName: readModelName, value: readModel } as ReadModelEnvelope, expectedCurrentVersion)
  } catch (e) {
    const error = e as NedbError
    // The error will be thrown, but in case of a conditional check, we throw the expected error type by the core
    if (error.errorType == UNIQUE_VIOLATED_ERROR_TYPE) {
      logger.warn(
        `Unique violated storing ReadModel ${JSON.stringify(
          readModel
        )} and expectedCurrentVersion ${expectedCurrentVersion}`
      )
      throw new OptimisticConcurrencyUnexpectedVersionError(error.message)
    }
    throw e
  }
  logger.debug('Read model stored')
  if (config.enableSubscriptions) {
    try {
      await graphQLService.handleNotificationSubscription([{ typeName: readModelName, value: readModel }])
      logger.debug('Read model change notified')
    } catch (e) {
      logger.error('Error notifying subscription', readModel)
    }
  }
}

export async function searchReadModel(
  db: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false,
  select?: ProjectionFor<unknown>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<Array<any> | ReadModelListResult<any>> {
  const logger = getLogger(config, 'read-model-adapter#searchReadModel')
  logger.debug('Converting filter to query')
  const queryFor = queryRecordFor(filters)
  const query = { ...queryFor, typeName: readModelName }
  logger.debug('Got query ', query)
  const skipId = afterCursor?.id ? parseInt(afterCursor?.id) : 0
  select = select?.map((item: string) => item.replace('!', '')) as ProjectionFor<unknown>
  const result = await db.query(query, sortBy, skipId, limit, select)
  // result = nestProperties(result)
  logger.debug('Search result: ', result)
  const items = result?.map((envelope) => envelope.value) ?? []
  if (paginatedVersion) {
    return {
      items: items,
      count: items?.length ?? 0,
      cursor: { id: ((limit ? limit : 1) + skipId).toString() },
    }
  }
  return items
}

export async function deleteReadModel(
  db: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  const logger = getLogger(config, 'read-model-adapter#deleteReadModel')
  logger.debug(`Entering to Read model deleted. ID=${readModel.id}.Name=${readModelName}`)
  await db.deleteById(readModel.id, readModelName)
  logger.debug(`Read model deleted. ID=${readModel.id}. Name=${readModelName}`)
}

// function nestProperties(obj: any): any {
//   const result = {}
//
//   function setNestedProperty(obj: any, path: string[], value: any): void {
//     let current = obj
//     for (let i = 0; i < path.length; i++) {
//       if (!current[path[i]]) {
//         current[path[i]] = {}
//       }
//       current = current[path[i]]
//     }
//     current[path[path.length - 1]] = value
//   }
//
//   function processObject(input: any, output: any): void {
//     for (const key in input) {
//       if (Object.prototype.hasOwnProperty.call(input, key)) {
//         const value = input[key]
//         const keys = key.split('.')
//         setNestedProperty(output, keys, value)
//       }
//     }
//   }
//
//   function processArray(arr: any[]): any[] {
//     return arr.map((item: any): any => {
//       if (Array.isArray(item)) {
//         return processArray(item)
//       } else if (item !== null && typeof item === 'object') {
//         const nestedItem = {}
//         processObject(item, nestedItem)
//         return nestedItem
//       } else {
//         return item
//       }
//     })
//   }
//
//   if (Array.isArray(obj)) {
//     return processArray(obj)
//   } else if (obj !== null && typeof obj === 'object') {
//     processObject(obj, result)
//   }
//
//   return result
// }
