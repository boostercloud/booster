import { BoosterConfig } from '@boostercloud/framework-types'

export const eventsStoreAttributes = {
  partitionKey: 'entityTypeName_entityID_kind',
  sortKey: 'createdAt',
} as const

export const subscriptionsStoreAttributes = {
  partitionKey: 'className',
  sortKey: 'connectionID_subscriptionID',
  ttl: 'expirationTime',
  indexByConnectionIDPartitionKey: 'connectionID',
  indexByConnectionIDSortKey: 'subscriptionID',
  indexByConnectionIDName: (config: BoosterConfig) => config.resourceNames.subscriptionsStore + '-index-by-connection',
} as const

export const connectionsStoreAttributes = {
  partitionKey: 'connectionID',
  ttl: 'expirationTime',
} as const

export const dedupAttributes = {
  partitionKey: 'primaryKey',
  ttl: 'expirationTime',
} as const

export const environmentVarNames = {
  restAPIURL: 'BOOSTER_REST_API_URL',
  websocketAPIURL: 'BOOSTER_WEBSOCKET_API_URL',
  cosmosDbConnectionString: 'COSMOSDB_CONNECTION_STRING',
  eventHubConnectionString: 'EVENTHUB_CONNECTION_STRING',
  eventHubName: 'EVENTHUB_NAME',
  eventHubMaxRetries: 'EVENTHUB_MAX_RETRIES',
  eventHubMode: 'EVENTHUB_MODE',
  rocketFunctionAppNames: 'ROCKET_FUNCTION_APP_NAMES',
} as const

// Azure special error codes
export const AZURE_CONFLICT_ERROR_CODE = 409
export const AZURE_PRECONDITION_FAILED_ERROR = 412
