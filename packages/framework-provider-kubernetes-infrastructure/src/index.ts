import { deploy, nuke, BoosterK8sConfiguration } from './infrastructure'
import { BoosterConfig, Logger, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'
export { BoosterK8sConfiguration }

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger),
  }
}
