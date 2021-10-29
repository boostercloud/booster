import { BoosterConfig } from './config'
import { Logger } from './logger'

export interface ProviderContext {
  logger: Logger
  config: BoosterConfig
  name: string
}
