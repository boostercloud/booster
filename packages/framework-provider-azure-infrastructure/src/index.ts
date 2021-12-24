import { deploy, nuke, synth } from './infrastructure'
import { BoosterConfig, Logger, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'
import { loadRocket } from './infrastructure/rockets/infrastructure-rocket'

export * from './test-helper/azure-test-helper'
export * from './infrastructure/types/application-synth-stack'
export * from './infrastructure/rockets/rocket-utils'
export * from './infrastructure/templates/index'
export * from './infrastructure/rockets/infrastructure-rocket'
export * from './infrastructure/types/functionDefinition'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(loadRocket)
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger, rockets),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger, rockets),
    synth: async (config: BoosterConfig, logger: Logger) => await synth(config, logger, rockets),
  }
}
