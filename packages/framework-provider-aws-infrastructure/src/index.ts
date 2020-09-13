import { ProviderInfrastructure, PluginDescriptor } from '@boostercloud/framework-types'
import { loadPlugin } from './infraestructure-plugin'
import { deploy, nuke } from './infrastructure'
export { InfrastructurePlugin } from './infraestructure-plugin'

export const Infrastructure = (pluginDescriptors: PluginDescriptor[]): ProviderInfrastructure => {
  const plugins = pluginDescriptors.map(loadPlugin)
  return {
    deploy: deploy.bind(null, plugins),
    nuke,
  }
}
