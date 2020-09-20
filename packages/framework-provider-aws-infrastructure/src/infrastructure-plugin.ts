import { BoosterConfig, PluginDescriptor } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'

export interface InfrastructurePlugin {
  mountStack: (config: BoosterConfig, stack: Stack) => void
}

// Separate function to make it easier to mock in tests
function requirePlugin(pluginPackageName: string): (params: unknown) => InfrastructurePlugin {
  return require(pluginPackageName)
}

export function loadPlugin(pluginDescriptor: PluginDescriptor): InfrastructurePlugin {
  const pluginBuilder = requirePlugin(pluginDescriptor.packageName)
  if (!pluginBuilder)
    throw new Error(
      `Could not load the plugin package ${pluginDescriptor.packageName}. Make sure you installed it in your project devDependencies.`
    )
  if (typeof pluginBuilder !== 'function')
    throw new Error(
      `Could not initialize plugin package ${pluginDescriptor.packageName}. It doesn't seem to implement a plugin builder method as default export.`
    )
  const plugin = pluginBuilder(pluginDescriptor.parameters)
  if (!plugin?.mountStack)
    throw new Error(
      `The package ${pluginDescriptor.packageName} doesn't seem to implement the required interface 'InfrastructureProvider' defined in package '@boostercloud/framework-provider-aws-infrastructure'.`
    )
  return plugin
}
