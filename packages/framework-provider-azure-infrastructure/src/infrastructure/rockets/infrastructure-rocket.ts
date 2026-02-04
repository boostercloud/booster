import { BoosterConfig } from '@boostercloud/framework-types'
import {
  FunctionAppFunctionsDefinitions,
  FunctionAppV4Definitions,
  FunctionDefinition,
} from '../types/functionDefinition'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { RocketUtils } from './rocket-utils'

export interface InfrastructureRocket {
  mountStack: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => Promise<ApplicationSynthStack>
  unmountStack?: () => void
  getFunctionAppName?: (applicationSynthStack: ApplicationSynthStack) => string
  // @deprecated use mountCode instead
  mountFunctions?: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => Array<FunctionDefinition>
  mountCode?: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => Promise<FunctionAppFunctionsDefinitions>
  mountFunctionsV4?: (
    config: BoosterConfig,
    applicationSynthStack: ApplicationSynthStack,
    utils: RocketUtils
  ) => Promise<FunctionAppV4Definitions>
}
