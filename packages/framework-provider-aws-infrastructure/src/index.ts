import { ProviderInfrastructure, PluginDescriptor, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { loadPlugin } from '@boostercloud/framework-provider-aws-infrastructure/src/infrastructure-plugin'
import { deploy, nuke } from './infrastructure'

export { InfrastructurePlugin } from './infrastructure-plugin'

export const Infrastructure = (pluginDescriptors?: PluginDescriptor[]): ProviderInfrastructure => ({
  deploy: async (config: BoosterConfig, logger: Logger) => {
    const plugins = pluginDescriptors?.map(loadPlugin)
    try {
      await deploy(config, logger, plugins)
    } catch (error) {
      logger.error(error)
    }
  },
  nuke: async (config: BoosterConfig, logger: Logger) => {
    try {
      await nuke(config, logger).catch(logger.error)
    } catch (error) {
      logger.error(error)
    }
  },
})
