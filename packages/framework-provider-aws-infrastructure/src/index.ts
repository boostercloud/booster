import { ProviderInfrastructure, RocketDescriptor, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { loadRocket, Rocket } from '@boostercloud/rockets-base'
import { deploy } from './infrastructure/deploy'
import { nuke } from './infrastructure/nuke'
import { AWSProviderContext } from './infrastructure/provider-context/aws-provider-context'

export * from './test-helper/aws-test-helper'

// Needed to extract this function to be able to rewire it in tests
function loadRockets(rocketDescriptors: RocketDescriptor<AWSProviderContext>[]): Rocket<AWSProviderContext>[] {
  return rocketDescriptors?.map(loadRocket)
}

export const Infrastructure = (rocketDescriptors?: RocketDescriptor<AWSProviderContext>[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors ? loadRockets(rocketDescriptors) : undefined
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => await deploy(config, logger, rockets),
    nuke: async (config: BoosterConfig, logger: Logger) => await nuke(config, logger, rockets),
  }
}
