import { BoosterConfig } from '@boostercloud/framework-types'
import Brand from '../common/brand'

export function assertNameIsCorrect(name: string): void {
  // Current characters max length: 37
  // Lambda name limit is 64 characters
  // `-subscriptions-notifier` lambda is 23 characters
  // `-app` prefix is added to application stack
  // which is 64 - 23 - 4 = 37
  const maxProjectNameLength = 37

  if (name.length > maxProjectNameLength)
    throw new ForbiddenProjectName(name, `be longer than ${maxProjectNameLength} characters`)

  if (name.includes(' ')) throw new ForbiddenProjectName(name, 'contain spaces')

  if (name.toLowerCase() !== name) throw new ForbiddenProjectName(name, 'contain uppercase letters')

  if (name.includes('_')) throw new ForbiddenProjectName(name, 'contain underscore')
}

class ForbiddenProjectName extends Error {
  constructor(public projectName: string, public restrictionText: string) {
    super(Brand.dangerize(`Your Booster project name cannot ${restrictionText}. You choose "${projectName}"`))
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function supportedInfrastructureMethodOrDie(
  methodName: 'deploy' | 'nuke' | 'start' | 'synth',
  config: BoosterConfig
): any {
  assertNameIsCorrect(config.appName)
  const method = config.provider.infrastructure()[methodName]
  if (!method) {
    throw new Error(
      `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.`
    )
  }
  return method
}

export function deployToCloudProvider(config: BoosterConfig): Promise<void> {
  return supportedInfrastructureMethodOrDie('deploy', config)(config)
}

export function synthToProvider(config: BoosterConfig): Promise<void> {
  return supportedInfrastructureMethodOrDie('synth', config)(config)
}

export function nukeCloudProviderResources(config: BoosterConfig): Promise<void> {
  return supportedInfrastructureMethodOrDie('nuke', config)(config)
}

export async function startProvider(port: number, config: BoosterConfig): Promise<void> {
  return supportedInfrastructureMethodOrDie('start', config)(config, port)
}
