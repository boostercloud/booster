import { deploy, nuke, synth } from './infrastructure'
import { BoosterConfig, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'
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
    deploy: async (config: BoosterConfig) => await deploy(config, rockets),
    nuke: async (config: BoosterConfig) => await nuke(config, rockets),
    synth: async (config: BoosterConfig) => await synth(config, rockets),
  }
}
