import { ServicePrincipal } from '../types/service-principal'
import { ResourceGroup } from '../types/resource-group'
import { runCommand } from '../../../../framework-common-helpers'

const azCommand = 'az'

export async function ensureAzCli() {
  console.log('Ensure azure-cli')
  const command = await runCommand('.', `${azCommand} version`)
  if (!command?.stdout?.toLocaleLowerCase().includes('azure-cli')) {
    throw new Error('Azure cli was not properly installed and is required to run the integration tests. ')
  }
}

export async function ensureServicePrincipal(servicePrincipalName: string): Promise<ServicePrincipal> {
  console.log('Ensure service principal')
  const command = await runCommand('.', `${azCommand} ad sp list --display-name ${servicePrincipalName}`, true)
  if (command?.stdout?.toLocaleLowerCase() === '[]') {
    throw new Error(`Service principal ${servicePrincipalName} not found.`)
  }
  return JSON.parse(command?.stdout)
}

export async function createServicePrincipal(servicePrincipalName: string): Promise<ServicePrincipal> {
  console.log(`Create service principal ${servicePrincipalName}`)
  const command = await runCommand('.', `${azCommand} ad sp create-for-rbac --name ${servicePrincipalName}`, true)
  if (!command?.stdout?.includes(servicePrincipalName)) {
    throw new Error(`Error creating service principal ${servicePrincipalName}. ${command.stderr}`)
  }
  return JSON.parse(command?.stdout)
}

export async function exportAzureConfiguration(servicePrincipal: ServicePrincipal, serviceRegion = 'East US') {
  console.log('Export Configuration')
  process.env['AZURE_APP_ID'] = servicePrincipal.appId
  process.env['AZURE_SECRET'] = servicePrincipal.password
  process.env['AZURE_TENANT_ID'] = servicePrincipal.tenant
  process.env['REGION'] = serviceRegion
  process.env['publisherEmail'] = 'noreply@booster.cloud'
  process.env['publisherName'] = 'Booster App'

  const command = await runCommand('.', `${azCommand} account show`, true)
  process.env['AZURE_SUBSCRIPTION_ID'] = JSON.parse(command?.stdout)?.id
}

export async function getResourceGroup(appName: string): Promise<ResourceGroup> {
  const resourceGroupName = `resource-group-${appName}-azure`
  console.log(`Get resource group ${appName}`)
  const command = await runCommand('.', `${azCommand} group show --name ${resourceGroupName}`, true)
  if (command?.stdout.includes('could not be found')) {
    return Promise.reject(`Resource Group for application ${appName} does not exist`)
  }
  return JSON.parse(command?.stdout)
}
