export const BOOSTER_HTTP_PORT = 'BOOSTER_HTTP_PORT'
export const boosterHTTPPort = (): string =>
  process.env[BOOSTER_HTTP_PORT] || process.env[BOOSTER_INTERNAL_LOCAL_PORT] || '3000'

export const BOOSTER_WS_PORT = 'BOOSTER_WS_PORT'
export const boosterWSPort = (): string =>
  process.env[BOOSTER_WS_PORT] || process.env[LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT] || '65529'

/**
 * @deprecated Use BOOSTER_HTTP_PORT instead
 */
export const BOOSTER_INTERNAL_LOCAL_PORT = 'BOOSTER_INTERNAL_LOCAL_PORT'

/**
 * @deprecated Use BOOSTER_WS_PORT instead
 */
export const LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT = 'LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT'

/**
 * @deprecated Use boosterHTTPPort instead
 */
export const boosterLocalPort = boosterHTTPPort
