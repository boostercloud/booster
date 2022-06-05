import { BoosterConfig } from '@boostercloud/framework-types'
import { Router } from 'express'

export interface InfrastructureRocket {
  mountStack: (config: BoosterConfig, router: Router) => void
  unmountStack?: () => void
}
