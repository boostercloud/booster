import { BoosterConfig } from '@boostercloud/framework-types'
import { Router } from 'express'

export interface InfrastructureRocketMetadata {
  port: number
}

export interface InfrastructureRocket {
  mountStack: (
    config: BoosterConfig,
    router: Router,
    infrastructureRocketMetadata?: InfrastructureRocketMetadata
  ) => void
  unmountStack?: () => void
}
