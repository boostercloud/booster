import { BoosterConfig } from '@boostercloud/framework-types'

export const eventsStoreAttributes = {
  partitionKey: 'entityTypeName_entityID_kind',
  sortKey: 'createdAt',
} as const

export const subscriptionsStoreAttributes = {
  partitionKey: 'typeName',
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

export const environmentVarNames = {
  restAPIURL: 'BOOSTER_REST_API_URL',
  websocketAPIURL: 'BOOSTER_WEBSOCKET_API_URL',
} as const
