import { BoosterConfig } from '@boostercloud/framework-types'
import { Router } from 'express'

export interface InfrastructureRocket {
  mountStack: (config: BoosterConfig, router: Router, port: number) => void
  unmountStack?: () => void
}
