import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { App } from 'cdktf'
import { AzureStack } from './azure-stack'
import { ZipResource } from './types/zip-resource'
import { FunctionZip } from './helper/function-zip'
import {
  azureCredentials,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createResourceManagementClient,
  renderToFile,
} from './helper/utils'
import { runCommand, Promises } from '@boostercloud/framework-common-helpers'
import * as ckdtfTemplate from './templates/cdktf'

export const synth = (configuration: BoosterConfig, logger: Logger): Promise<void> => synthApp(logger, configuration)

export const deploy = (configuration: BoosterConfig, logger: Logger): Promise<void> => deployApp(logger, configuration)

export const nuke = (configuration: BoosterConfig, logger: Logger): Promise<void> => nukeApp(logger, configuration)

/**
 * Synth the application for Azure
 */
async function synthApp(logger: Logger, config: BoosterConfig): Promise<void> {
  await terraformSynth(logger, config)
}

async function uploadFile(logger: Logger, config: BoosterConfig, zipResource: ZipResource): Promise<void> {
  logger.info('Uploading zip file')
  const resourceGroupName = createResourceGroupName(config.appName, config.environmentName)
  const functionAppName = createFunctionResourceGroupName(resourceGroupName)
  await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
  logger.info('Zip file uploaded')
}

/**
 * Deploys the application in Azure
 */
async function deployApp(logger: Logger, config: BoosterConfig): Promise<void> {
  logger.info(`Deploying app ${config.appName}`)
  const zipResource = await terraformSynth(logger, config)
  const command = await runCommand(process.cwd(), 'npx cdktf deploy --auto-approve')
  if (command.childProcess.exitCode !== 0) {
    return Promise.reject(`Deploy application ${config.appName} failed. Check cdktf logs`)
  }
  await uploadFile(logger, config, zipResource)
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

async function generateSynthFiles(logger: Logger, config: BoosterConfig): Promise<void> {
  logger.info('Generating cdktf files')
  const filesToGenerate: Array<[Array<string>, string]> = [[['cdktf.json'], ckdtfTemplate.template]]
  await Promises.allSettledAndFulfilled(filesToGenerate.map(renderToFile(config)))
}

async function generateZipFile(logger: Logger, config: BoosterConfig): Promise<ZipResource> {
  logger.info('Generating zip file')
  const zipResource = await FunctionZip.copyZip(config)
  logger.info('Generated zip file', zipResource)
  return zipResource
}

async function terraformSynth(logger: Logger, config: BoosterConfig): Promise<ZipResource> {
  await generateSynthFiles(logger, config)
  const zipResource = await generateZipFile(logger, config)
  logger.info('Synth...')
  const app = new App()
  new AzureStack(app, config.appName + config.environmentName)
  app.synth()
  return zipResource
}
