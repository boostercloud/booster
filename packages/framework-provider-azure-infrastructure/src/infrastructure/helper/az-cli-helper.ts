import { ResourceGroup } from '../types/resource-group'
import { runCommand } from '../../../../framework-common-helpers'

const azCommand = 'az'

export async function getResourceGroup(appName: string): Promise<ResourceGroup> {
  const resourceGroupName = `resource-group-${appName}-azure`
  console.log(`Get resource group ${appName}`)
  const command = await runCommand('.', `${azCommand} group show --name ${resourceGroupName}`, true)
  if (command?.stdout.includes('could not be found')) {
    return Promise.reject(`Resource Group for application ${appName} does not exist`)
  }
  return JSON.parse(command?.stdout)
}
