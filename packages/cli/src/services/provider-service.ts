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

export const deployToCloudProvider = (config: BoosterConfig): Observable<string> => {
  assertNameIsCorrect(config.appName)
  const deployMethod = config.provider.infrastructure().deploy
  if (!deployMethod) {
    throw new Error(
      'Attempted to deploy with a provider that does not support deploying the project, perhaps you meant `boost run`?'
    )
  }
  return deployMethod(config)
}

export async function runProvider(port: number, config: BoosterConfig): Promise<void> {
  assertNameIsCorrect(config.appName)
  const runMethod = config.provider.infrastructure().run
  if (!runMethod) {
    throw new Error(
      'Attempted to run with a provider that is does not support running the project, perhaps you meant `boost deploy`?'
    )
  }
  return Promise.resolve(runMethod(config, port))
}

export const nukeCloudProviderResources = (config: BoosterConfig): Observable<string> => {
  return config.provider.infrastructure().nuke(config)
}
