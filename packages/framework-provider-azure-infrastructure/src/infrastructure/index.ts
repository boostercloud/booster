limport { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import {
  azureCredentials,
  createResourceGroup,
  createResourceGroupName,
  createResourceManagementClient,
  createWebSiteManagementClient,
} from './setup'

export function deploy(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    nukeApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

/**
 * Deploys the application in Azure
 */
async function deployApp(observer: Subscriber<string>, config: BoosterConfig): Promise<void> {
  const credentials = await azureCredentials()
  const [resourceManagementClient, webSiteManagementClient] = await Promise.all([
    createResourceManagementClient(credentials),
    createWebSiteManagementClient(credentials),
  ])
  const resourceGroupName = createResourceGroupName(config)
  await createResourceGroup(resourceGroupName, resourceManagementClient)
  const applicationBuilder = new ApplicationStackBuilder(config)
  await applicationBuilder.buildOn(observer, resourceManagementClient, webSiteManagementClient, resourceGroupName)
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(observer: Subscriber<string>, config: BoosterConfig): Promise<void> {
  const credentials = await azureCredentials()
  const resourceManagementClient = await createResourceManagementClient(credentials)

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(createResourceGroupName(config))
}
