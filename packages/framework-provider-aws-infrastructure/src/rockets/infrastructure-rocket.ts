import { BoosterConfig } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import { RocketUtils } from './rocket-utils'

export interface InfrastructureRocket {
  mountStack: (stack: Stack, config: BoosterConfig) => void
  unmountStack?: (utils: RocketUtils) => void
}
