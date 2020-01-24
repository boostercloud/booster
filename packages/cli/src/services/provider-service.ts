import { BoosterConfig } from '@boostercloud/framework-types'
import { Observable } from 'rxjs'
import { Providers } from '@boostercloud/framework-core'

export const deployToCloudProvider = (configuration: BoosterConfig): Observable<string> => {
  return Providers.getInfrastructure(configuration).deploy(configuration)
}
export const nukeCloudProviderResources = (configuration: BoosterConfig): Observable<string> => {
  return Providers.getInfrastructure(configuration).nuke(configuration)
}
