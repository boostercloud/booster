import { configuration } from './params'
import { ApplicationTokenCredentials, loginWithServicePrincipalSecret } from 'ms-rest-azure'
import WebSiteManagement from 'azure-arm-website'
import { toAzureName } from './utils'
import { BoosterConfig } from '@boostercloud/framework-types'

const MAX_RESOURCE_GROUP_NAME_SIZE = 21

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

export function createResourceGroupName(config: BoosterConfig): string {
  return `${toAzureName(config.appName + config.environmentName, MAX_RESOURCE_GROUP_NAME_SIZE)}rg`
}

export function createFunctionResourceGroupName(resourceGroupName: string): string {
  return `${resourceGroupName}func`
}
