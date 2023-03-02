import { ResourceGroup } from '../types/resource-group'
import { Resource } from '../types/resource'
import { createResourceGroupName } from './utils'
import { runCommand } from '@boostercloud/framework-common-helpers'

const azCommand = 'az'

export async function getResourceGroup(appName: string, environmentName: string): Promise<ResourceGroup> {
  const resourceGroupName = createResourceGroupName(appName, environmentName)
  console.log(`Get resource group ${resourceGroupName}`)
  const commandOutput = await runCommand('.', `${azCommand} group show --name ${resourceGroupName}`, true)
  if (commandOutput.includes('could not be found')) {
    return Promise.reject(`Resource Group for application ${appName} does not exist`)
  }
  console.log(`Command success ${commandOutput}`)
  return JSON.parse(commandOutput)
}

export async function showResourcesInResourceGroup(resourceGroupName: string): Promise<[Resource]> {
  const commandOutput = await runCommand('.', `${azCommand} resource list --resource-group ${resourceGroupName}`, true)
  if (commandOutput.includes('could not be found')) {
    return Promise.reject(`Resource Group ${resourceGroupName} does not exist`)
  }
  return JSON.parse(commandOutput)
}

export async function showResourceInfo(
  resourceGroupName: string,
  resourceName: string,
  resourceType: string
): Promise<Resource> {
  const commandOutput = await runCommand(
    '.',
    `${azCommand} resource show --resource-group ${resourceGroupName} --name ${resourceName} --resource-type ${resourceType}`,
    true
  )
  if (commandOutput.includes('could not be found')) {
    return Promise.reject(`Resource ${resourceName} does not exist`)
  }
  return JSON.parse(commandOutput)
}

export async function getCosmosConnectionStrings(
  appName: string,
  environmentName: string
): Promise<{ [key: string]: any }> {
  const resourceGroupName = createResourceGroupName(appName, environmentName)
  const commandOutput = await runCommand(
    '.',
    `${azCommand} cosmosdb list-connection-strings --resource-group ${resourceGroupName} --name ${resourceGroupName}cdba`,
    true
  )
  if (commandOutput.includes('could not be found')) {
    return Promise.reject(`Resource ${resourceGroupName} does not exist`)
  }
  return JSON.parse(commandOutput)
}
