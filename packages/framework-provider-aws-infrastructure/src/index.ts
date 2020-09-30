import { ProviderInfrastructure, PluginDescriptor, BoosterConfig, Logger } from '@boostercloud/framework-types'
import { loadPlugin } from '@boostercloud/framework-provider-aws-infrastructure/src/infrastructure-plugin'
import { deploy, nuke } from './infrastructure'

export { InfrastructurePlugin } from './infrastructure-plugin'

export const Infrastructure = (pluginDescriptors?: PluginDescriptor[]): ProviderInfrastructure => ({
  deploy: (config: BoosterConfig, logger: Logger) => {
    const plugins = pluginDescriptors?.map(loadPlugin)
    return deploy(config, logger, plugins)
  },
  nuke,
})
