import { BoosterConfig } from '@boostercloud/framework-types'
import { Observable } from 'rxjs'

export function assertNameIsCorrect(name: string): void {
  // It is 55 because cloudformations max length is 63.
  // Booster creates an S3 bucket ended in '-toolkit'
  // which is 8 chars long. 63 - 8 = 55
  const maxProjectNameLength = 55
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
  return config.provider.getInfrastructure().deploy(config)
}
export const nukeCloudProviderResources = (config: BoosterConfig): Observable<string> => {
  return config.provider.getInfrastructure().nuke(config)
}
