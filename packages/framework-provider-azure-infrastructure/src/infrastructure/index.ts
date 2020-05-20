import { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { ApplicationTokenCredentials, loginWithServicePrincipalSecret } from 'ms-rest-azure'
import { ResourceGroup } from 'azure-arm-resource/lib/resource/models'
import { configuration } from './params'
import { ApplicationStackBuilder } from './stacks/application-stack'

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
  const resourceManagementClient = await createResourceManagementClient()
  const resourceGroupName = createResourceGroupName(config)
  await createResourceGroup(resourceGroupName, resourceManagementClient)
  new ApplicationStackBuilder(config).buildOn(resourceManagementClient, resourceGroupName)
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(observer: Subscriber<string>, config: BoosterConfig): Promise<void> {
  const resourceManagementClient = await createResourceManagementClient()

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(createResourceGroupName(config))
}

async function createResourceManagementClient():Promise<ResourceManagementClient> {
  const credentials = await azureCredentials()
  return new ResourceManagementClient(credentials, configuration.subscriptionId)
}

export async function azureCredentials(): Promise<ApplicationTokenCredentials> {
  const applicationTokenCredentials = await loginWithServicePrincipalSecret(
    configuration.appId,
    configuration.secret,
    configuration.tenantId);

  if(!applicationTokenCredentials) {
    throw new Error('Unable to login with Service Principal. Please verified provided appId, secret and subscription ID in .env file are correct.')
  }

  return applicationTokenCredentials
}

async function createResourceGroup(resourceGroupName: string, resourceManagementClient: ResourceManagementClient) {
  const existed = await resourceManagementClient.resourceGroups.checkExistence(resourceGroupName)

  if(!existed) {
    const groupParameters: ResourceGroup = { location: configuration.region }
    await resourceManagementClient.resourceGroups.createOrUpdate(resourceGroupName, groupParameters)
  }
}

function createResourceGroupName(config: BoosterConfig): string {
  return 'rg-' + config.appName + '-' + config.environmentName
}