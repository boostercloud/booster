export const BOOSTER_LOCAL_PORT = 'BOOSTER_INTERNAL_LOCAL_PORT'

export const boosterLocalPort = (): string => process.env[BOOSTER_LOCAL_PORT] || '3000'
