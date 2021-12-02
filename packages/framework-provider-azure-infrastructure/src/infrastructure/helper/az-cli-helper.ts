import { ResourceGroup } from '../types/resource-group'
import { runCommand } from '../../../../framework-common-helpers'
import { Resource } from '../types/resource'
import { createResourceGroupName } from './utils'

const azCommand = 'az'

export async function getResourceGroup(appName: string, environmentName: string): Promise<ResourceGroup> {
  const resourceGroupName = createResourceGroupName(appName, environmentName)
  console.log(`Get resource group ${resourceGroupName}`)
  const command = await runCommand('.', `${azCommand} group show --name ${resourceGroupName}`, true)
  if (command?.stdout.includes('could not be found')) {
    return Promise.reject(`Resource Group for application ${appName} does not exist`)
  }
  return JSON.parse(command?.stdout)
}

export async function showResourcesInResourceGroup(resourceGroupName: string): Promise<[Resource]> {
  const command = await runCommand('.', `${azCommand} resource list --resource-group ${resourceGroupName}`, true)
  if (command?.stdout.includes('could not be found')) {
    return Promise.reject(`Resource Group ${resourceGroupName} does not exist`)
  }
  return JSON.parse(command?.stdout)
}

export async function showResourceInfo(
  resourceGroupName: string,
  resourceName: string,
  resourceType: string
): Promise<Resource> {
  const command = await runCommand(
    '.',
    `${azCommand} resource show --resource-group ${resourceGroupName} --name ${resourceName} --resource-type ${resourceType}`,
    true
  )
  if (command?.stdout.includes('could not be found')) {
    return Promise.reject(`Resource ${resourceName} does not exist`)
  }
  return JSON.parse(command?.stdout)
}
