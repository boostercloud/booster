export const eventsStoreAttributes = {
  partitionKey: 'entityTypeName_entityID_kind',
  sortKey: 'createdAt',
} as const

export const subscriptionsStoreAttributes = {
  partitionKey: 'typeName',
  sortKey: 'connectionID',
  ttl: 'expirationTime',
  indexByConnectionIDPartitionKey: 'connectionID',
  indexByConnectionIDSortKey: 'subscriptionID',
  indexByConnectionIDNameSuffix: '-index-by-connection',
} as const

export const environmentVarNames = {
  restAPIURL: 'BOOSTER_REST_API_URL',
  websocketAPIURL: 'BOOSTER_WEBSOCKET_API_URL',
} as const
