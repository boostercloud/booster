export const eventStorePartitionKeyAttribute = 'entityTypeName_entityID_kind'
export const eventStoreSortKeyAttribute = 'createdAt'
export const subscriptionsStorePartitionKeyAttribute = 'typeName'
export const subscriptionsStoreSortKeyAttribute = 'connectionID'
export const subscriptionsStoreTTLAttribute = 'expirationTime'
export const environmentVarNames = {
  restAPIURL: 'BOOSTER_REST_API_URL',
  websocketAPIURL: 'BOOSTER_WEBSOCKET_API_URL',
} as const
