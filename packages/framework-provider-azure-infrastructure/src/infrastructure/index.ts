import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import {
  azureCredentials,
  createResourceGroup,
  createResourceGroupName,
  createResourceManagementClient,
  createWebSiteManagementClient,
} from './setup'
import { InfrastructureRocket } from '..'
import { RocketsStackBuilder } from './stacks/rockets-stack'

export const deploy = (configuration: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> =>
  deployApp(logger, configuration, rockets)

export const nuke = (configuration: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> =>
  nukeApp(logger, configuration, rockets)

/**
 * Deploys the application in Azure
 */
async function deployApp(logger: Logger, config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const credentials = await azureCredentials()
  const [resourceManagementClient, webSiteManagementClient] = await Promise.all([
    createResourceManagementClient(credentials),
    createWebSiteManagementClient(credentials),
  ])
  const resourceGroupName = createResourceGroupName(config)
  await createResourceGroup(resourceGroupName, resourceManagementClient)
  const applicationBuilder = new ApplicationStackBuilder(config)
  const applicationBuilderConfig = await applicationBuilder.buildOn(
    logger,
    resourceManagementClient,
    webSiteManagementClient,
    resourceGroupName
  )
  const rocketsBuilder = new RocketsStackBuilder(config, applicationBuilderConfig, resourceManagementClient, rockets)
  await rocketsBuilder.build()
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(_logger: Logger, config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const credentials = await azureCredentials()
  const resourceManagementClient = await createResourceManagementClient(credentials)

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(createResourceGroupName(config))
}
