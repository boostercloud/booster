import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import {
  azureCredentials,
  createResourceGroup,
  createResourceGroupName,
  createResourceManagementClient,
  createWebSiteManagementClient,
} from './setup'

export const deploy = (configuration: BoosterConfig, logger: Logger): Promise<void> => deployApp(logger, configuration)

export const nuke = (configuration: BoosterConfig, logger: Logger): Promise<void> => nukeApp(logger, configuration)

/**
 * Deploys the application in Azure
 */
async function deployApp(logger: Logger, config: BoosterConfig): Promise<void> {
  const credentials = await azureCredentials()
  const [resourceManagementClient, webSiteManagementClient] = await Promise.all([
    createResourceManagementClient(credentials),
    createWebSiteManagementClient(credentials),
  ])
  const resourceGroupName = createResourceGroupName(config)
  await createResourceGroup(resourceGroupName, resourceManagementClient)
  const applicationBuilder = new ApplicationStackBuilder(config)
  await applicationBuilder.buildOn(logger, resourceManagementClient, webSiteManagementClient, resourceGroupName)
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(_logger: Logger, config: BoosterConfig): Promise<void> {
  const credentials = await azureCredentials()
  const resourceManagementClient = await createResourceManagementClient(credentials)

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(createResourceGroupName(config))
}
