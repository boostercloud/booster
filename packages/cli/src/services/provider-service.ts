import { BoosterConfig } from '@boostercloud/framework-types'
import { Observable } from 'rxjs'

export function assertNameIsCorrect(name: string): void {
  // Current characters max length: 37
  // Lambda name limit is 64 characters
  // `-subscriptions-notifier` lambda is 23 characters
  // `-app` prefix is added to application stack
  // which is 64 - 23 - 4 = 37
  const maxProjectNameLength = 37
  if (name.length > maxProjectNameLength)
    throw new Error(`Project name cannot be longer than ${maxProjectNameLength} chars long:

    Found: '${name}'`)

  if (name.includes(' '))
    throw new Error(`Project name cannot contain spaces:

    Found: '${name}'`)

  if (name.toLowerCase() != name)
    throw new Error(`Project name cannot contain uppercase letters:

    Found: '${name}'`)
}

function supportedInfrastructureMethodOrDie(methodName: 'deploy' | 'nuke' | 'start', config: BoosterConfig): any {
  assertNameIsCorrect(config.appName)
  const method = config.provider.infrastructure()[methodName]
  if (!method) {
    throw new Error(
      `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.` 
    )
  }
  return method
}

export function deployToCloudProvider(config: BoosterConfig): Observable<string> {
  return supportedInfrastructureMethodOrDie('deploy', config)(config)
}

export function nukeCloudProviderResources(config: BoosterConfig): Observable<string> {
  return supportedInfrastructureMethodOrDie('nuke', config)(config)
}

export async function startProvider(port: number, config: BoosterConfig): Promise<void> {
  return supportedInfrastructureMethodOrDie('start', config)(config, port)
}