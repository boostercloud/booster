import { BoosterConfig } from '@boostercloud/framework-types'
import { FunctionDefinition } from '../types/functionDefinition'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { RocketUtils } from './rocket-utils'

export interface InfrastructureRocket {
  mountStack: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => ApplicationSynthStack
  unmountStack?: () => void
  getFunctionAppName?: (applicationSynthStack: ApplicationSynthStack) => string
  mountFunctions?: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => Array<FunctionDefinition>
}
