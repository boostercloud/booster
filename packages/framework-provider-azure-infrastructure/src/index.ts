import { deploy, nuke } from './infrastructure'
import { BoosterConfig, Logger, ProviderInfrastructure, RocketDescriptor } from '@boostercloud/framework-types'

export * from './test-helper/azure-test-helper'
import { loadRocket } from './rockets/infrastructure-rocket'

export { InfrastructureRocket } from './rockets/infrastructure-rocket'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(loadRocket)
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger, rockets),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger, rockets),
  }
}
