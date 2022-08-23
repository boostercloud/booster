import { BoosterConfig } from '@boostercloud/framework-types'
import { azureCredentials, createResourceGroupName, createResourceManagementClient } from './helper/utils'
import { runCommand, getLogger } from '@boostercloud/framework-common-helpers'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { ApplicationBuilder } from './application-builder'
import { RocketBuilder } from './rockets/rocket-builder'

export const synth = (config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> =>
  synthApp(config, rockets)

export const deploy = (config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> =>
  deployApp(config, rockets)

export const nuke = (config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> => nukeApp(config, rockets)

/**
 * Synth the application for Azure
 */
async function synthApp(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const applicationBuilder = new ApplicationBuilder(config, rockets)
  await applicationBuilder.buildApplication()
}

/**
 * Deploys the application in Azure
 */
async function deployApp(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const logger = getLogger(config, 'index#deployApp')
  logger.info(`Deploying app ${config.appName}`)
  const applicationBuilder = new ApplicationBuilder(config, rockets)
  const applicationBuild = await applicationBuilder.buildApplication()

  const command = await runCommand(process.cwd(), `npx cdktf deploy --auto-approve`)
  if (command.childProcess.exitCode !== 0) {
    return Promise.reject(`Deploy application ${config.appName} failed. Check cdktf logs`)
  }

  await applicationBuilder.uploadFile(applicationBuild.zipResource)
  if (applicationBuild.rocketsZipResources && applicationBuild.rocketsZipResources.length > 0) {
    const rocketBuilder = new RocketBuilder(config, applicationBuild.azureStack.applicationStack, rockets)
    rocketBuilder.uploadRocketsFiles(applicationBuild.rocketsZipResources)
  }
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const credentials = await azureCredentials()
  const resourceManagementClient = await createResourceManagementClient(credentials)

  rockets?.forEach((rocket) => rocket.unmountStack?.())

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(
    createResourceGroupName(config.appName, config.environmentName)
  )
}
