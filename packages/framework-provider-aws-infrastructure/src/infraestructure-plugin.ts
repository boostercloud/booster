import { BoosterConfig, PluginDescriptor } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'

export interface InfrastructurePlugin {
  mountStack: (config: BoosterConfig, stack: Stack) => void
}

export function loadPlugin(pluginDescriptor: PluginDescriptor): InfrastructurePlugin {
  const pluginBuilder = require(pluginDescriptor.packageName)
  if (!pluginBuilder)
    throw new Error(
      `Could not load the plugin package ${pluginDescriptor.packageName}. Make sure you've installed it in your project devDependencies.`
    )
  const plugin = pluginBuilder(pluginDescriptor.parameters)
  if (!plugin?.mountStack)
    throw new Error(
      `The package ${pluginDescriptor.packageName} doesn't seem to implement the required interface 'InfrastructureProvider' defined in package '@boostercloud/framework-provider-aws-infrastructure'.`
    )
  return plugin
}
