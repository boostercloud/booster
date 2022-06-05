import { ProviderInfrastructure, RocketDescriptor, BoosterConfig } from '@boostercloud/framework-types'
import { RocketLoader } from '@boostercloud/framework-common-helpers'
import { deploy, nuke } from './infrastructure'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'

export { InfrastructureRocket } from './rockets/infrastructure-rocket'
export { RocketUtils } from './rockets/rocket-utils'
export * from './test-helper/aws-test-helper'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(RocketLoader.loadRocket) as InfrastructureRocket[]
  return {
    deploy: async (config: BoosterConfig) => await deploy(config, rockets),
    nuke: async (config: BoosterConfig) => await nuke(config, rockets),
  }
}
