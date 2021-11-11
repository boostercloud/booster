import { BoosterConfig, RocketDescriptor } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'

export interface InfrastructureRocket {
  mountStack: (
    config: BoosterConfig,
    resourceManagementClient: ResourceManagementClient,
    resourceGroupName: string,
    functionAppName: string
  ) => Promise<void>
  unmountStack?: () => void
}

function requireRocket(rocketPackageName: string): (params: unknown) => InfrastructureRocket {
  return require(rocketPackageName).default
}

export function loadRocket(rocketDescriptor: RocketDescriptor): InfrastructureRocket {
  const rocketBuilder = requireRocket(rocketDescriptor.packageName)
  if (!rocketBuilder)
    throw new Error(
      `Could not load the rocket package ${rocketDescriptor.packageName}. Make sure you installed it in your project devDependencies.`
    )
  if (typeof rocketBuilder !== 'function')
    throw new Error(
      `Could not initialize rocket package ${rocketDescriptor.packageName}. It doesn't seem to implement a rocket builder method as default export.`
    )
  const rocket = rocketBuilder(rocketDescriptor.parameters)
  if (!rocket?.mountStack)
    throw new Error(
      `The package ${rocketDescriptor.packageName} doesn't seem to implement the required interface 'InfrastructureProvider' defined in package '@boostercloud/framework-provider-azure-infrastructure'.`
    )
  return rocket
}
