import { ProviderInfrastructure, RocketDescriptor, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { loadRocket } from '@boostercloud/rockets-base'
import { deploy } from './infrastructure/deploy'
import { nuke } from './infrastructure/nuke'
import { AWSProviderContext } from './infrastructure/provider-context/aws-provider-context'
export * from './test-helper/aws-test-helper'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor<AWSProviderContext>[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(loadRocket)
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger, rockets),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger, rockets),
  }
}
