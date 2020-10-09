import { ProviderInfrastructure, RocketDescriptor, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { loadRocket } from './rockets/infrastructure-rocket'
import { deploy, nuke } from './infrastructure'

export { InfrastructureRocket } from './rockets/infrastructure-rocket'
export { RocketUtils } from './rockets/rocket-utils'

export const Infrastructure = (rocketDescriptors?: RocketDescriptor[]): ProviderInfrastructure => {
  const rockets = rocketDescriptors?.map(loadRocket)
  return {
    deploy: async (config: BoosterConfig, logger: Logger) => {
      try {
        await deploy(config, logger, rockets)
      } catch (error) {
        logger.error(error)
      }
    },
    nuke: async (config: BoosterConfig, logger: Logger) => {
      try {
        await nuke(config, logger, rockets)
      } catch (error) {
        logger.error(error)
      }
    },
  }
}
