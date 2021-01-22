import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { configuration } from './params'
import { ApplicationTokenCredentials, loginWithServicePrincipalSecret } from 'ms-rest-azure'
import { ResourceGroup } from 'azure-arm-resource/lib/resource/models'
import { BoosterConfig } from '@boostercloud/framework-types'
import WebSiteManagement from 'azure-arm-website'

export async function createResourceManagementClient(
  credentials: ApplicationTokenCredentials
): Promise<ResourceManagementClient> {
  return new ResourceManagementClient(credentials, configuration.subscriptionId)
}

export async function createWebSiteManagementClient(
  credentials: ApplicationTokenCredentials
): Promise<WebSiteManagement> {
  return new WebSiteManagement(credentials, configuration.subscriptionId)
}

export async function azureCredentials(): Promise<ApplicationTokenCredentials> {
  const applicationTokenCredentials = await loginWithServicePrincipalSecret(
    configuration.appId,
    configuration.secret,
    configuration.tenantId
  )

  if (!applicationTokenCredentials) {
    throw new Error(
      'Unable to login with Service Principal. Please verified provided appId, secret and subscription ID in .env file are correct.'
    )
  }

  return applicationTokenCredentials
}

export async function createResourceGroup(
  resourceGroupName: string,
  resourceManagementClient: ResourceManagementClient
) {
  const existed = await resourceManagementClient.resourceGroups.checkExistence(resourceGroupName)

  if (!existed) {
    const groupParameters: ResourceGroup = { location: configuration.region }
    await resourceManagementClient.resourceGroups.createOrUpdate(resourceGroupName, groupParameters)
  }
}

export function createResourceGroupName(config: BoosterConfig): string {
  return 'resource-group-' + config.appName + '-' + config.environmentName
}
