import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { azureCredentials, createResourceGroupName, createResourceManagementClient } from './helper/utils'
import { runCommand } from '@boostercloud/framework-common-helpers'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { ApplicationBuilder } from './application-builder'
import { RocketBuilder } from './rockets/rocket-builder'

export const synth = (configuration: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> =>
  synthApp(logger, configuration, rockets)

export const deploy = (configuration: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> =>
  deployApp(logger, configuration, rockets)

export const nuke = (configuration: BoosterConfig, logger: Logger): Promise<void> => nukeApp(logger, configuration)

/**
 * Synth the application for Azure
 */
async function synthApp(logger: Logger, config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const applicationBuilder = new ApplicationBuilder(logger, config, rockets)
  await applicationBuilder.buildApplication()
}

/**
 * Deploys the application in Azure
 */
async function deployApp(logger: Logger, config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  logger.info(`Deploying app ${config.appName}`)
  const applicationBuilder = new ApplicationBuilder(logger, config, rockets)
  const applicationBuild = await applicationBuilder.buildApplication()

  const command = await runCommand(process.cwd(), 'npx cdktf deploy --auto-approve')
  if (command.childProcess.exitCode !== 0) {
    return Promise.reject(`Deploy application ${config.appName} failed. Check cdktf logs`)
  }
  const rocketBuilder = new RocketBuilder(logger, config, applicationBuild.azureStack.applicationStack, rockets)

  await applicationBuilder.uploadFile(applicationBuild.zipResource)
  await rocketBuilder.uploadRocketsFiles(applicationBuild.rocketZipResource)
}

/**
 * Nuke all the resources used in the Resource Group
 */
async function nukeApp(_logger: Logger, config: BoosterConfig): Promise<void> {
  const credentials = await azureCredentials()
  const resourceManagementClient = await createResourceManagementClient(credentials)

  // By deleting the resource group we are deleting all the resources within it.
  await resourceManagementClient.resourceGroups.deleteMethod(
    createResourceGroupName(config.appName, config.environmentName)
  )
}
