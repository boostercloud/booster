import { deploy, nuke, BoosterK8sConfiguration } from './infrastructure'
import { BoosterConfig, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'
export { BoosterK8sConfiguration }

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  return {
    deploy: async (config: BoosterConfig) => await deploy(config),
    nuke: async (config: BoosterConfig) => await nuke(config),
  }
}
