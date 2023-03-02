export const eventsStoreAttributes = {
  partitionKey: 'entityTypeName_entityID_kind',
  sortKey: 'createdAt',
} as const

export const environmentVarNames = {
  restAPIURL: 'BOOSTER_REST_API_URL',
  websocketAPIURL: 'BOOSTER_WEBSOCKET_API_URL',
  cosmosDbConnectionString: 'COSMOSDB_CONNECTION_STRING',
} as const

// Azure special error codes
export const AZURE_CONFLICT_ERROR_CODE = 409
export const AZURE_PRECONDITION_FAILED_ERROR = 412
