import { deploy, nuke, synth } from './infrastructure'
import { BoosterConfig, Logger, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'

export * from './test-helper/azure-test-helper'

export * from './infrastructure/synth/application-synth'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger),
    synth: async (config: BoosterConfig, logger: Logger) => await synth(config, logger),
  }
}
