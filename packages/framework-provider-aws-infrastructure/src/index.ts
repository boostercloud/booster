import { ProviderInfrastructure, PluginDescriptor, BoosterConfig } from '@boostercloud/framework-types'
import { loadPlugin } from '@boostercloud/framework-provider-aws-infrastructure/src/infrastructure-plugin'
import { deploy, nuke } from './infrastructure'
export { InfrastructurePlugin } from '@boostercloud/framework-provider-aws-infrastructure/src/infrastructure-plugin'

export const Infrastructure = (pluginDescriptors?: PluginDescriptor[]): ProviderInfrastructure => ({
  deploy: (config: BoosterConfig) => {
    const plugins = pluginDescriptors?.map(loadPlugin)
    return deploy(config, plugins)
  },
  nuke,
})
