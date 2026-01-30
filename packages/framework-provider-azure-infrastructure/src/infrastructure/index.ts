import { BoosterConfig } from '@boostercloud/framework-types'
import {
  azureCredentials,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createResourceManagementClient,
  createStreamFunctionResourceGroupName,
} from './helper/utils'
import { runCommand, getLogger } from '@boostercloud/framework-common-helpers'

const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms))
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { ApplicationBuilder } from './application-builder'
import { RocketBuilder } from './rockets/rocket-builder'
import { FunctionZip } from './helper/function-zip'
import * as childProcess from 'child_process'
import * as util from 'util'

const exec = util.promisify(childProcess.exec)

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

  try {
    await runCommand(process.cwd(), 'npx cdktf-cli deploy --auto-approve --ignore-missing-stack-dependencies')
  } catch (error) {
    return Promise.reject(`Deployment of application ${config.appName} failed. Check cdktf logs. \n${error.message}}`)
  }

  const resourceGroupName = createResourceGroupName(config.appName, config.environmentName)
  const functionAppName = createFunctionResourceGroupName(resourceGroupName)
  await FunctionZip.deployZip(config, functionAppName, resourceGroupName, applicationBuild.zipResource)
  if (config.eventStreamConfiguration.enabled) {
    const streamFunctionAppName = createStreamFunctionResourceGroupName(resourceGroupName)
    await FunctionZip.deployZip(config, streamFunctionAppName, resourceGroupName, applicationBuild.consumerZipResource!)
  }
  if (applicationBuild.rocketsZipResources && applicationBuild.rocketsZipResources.length > 0) {
    const rocketBuilder = new RocketBuilder(config, applicationBuild.azureStack.applicationStack, rockets)
    await rocketBuilder.uploadRocketsFiles(applicationBuild.rocketsZipResources)
  }

  // Update Web PubSub hub with the correct function key after functions are deployed
  if (config.enableSubscriptions) {
    const webPubSubName = `${resourceGroupName}wps`
    await updateWebPubSubHub(config, resourceGroupName, functionAppName, webPubSubName, 'booster')
  }
}

/**
 * Updates the Web PubSub hub with the correct webpubsub_extension key.
 * This must run after the full function ZIP is deployed, as that's when
 * the Azure Functions runtime discovers the WebPubSub triggers and creates the key.
 */
async function updateWebPubSubHub(
  config: BoosterConfig,
  resourceGroupName: string,
  functionAppName: string,
  webPubSubName: string,
  hubName: string
): Promise<void> {
  const logger = getLogger(config, 'index#updateWebPubSubHub')
  logger.info('Updating Web PubSub hub with function key...')

  // First, trigger the function app to start by hitting an endpoint
  logger.info('Triggering function app cold start...')
  try {
    await exec(
      `curl -s -o /dev/null "https://${functionAppName}.azurewebsites.net/api/graphql" -X POST -H "Content-Type: application/json" -d '{"query":"{ __typename }"}'`
    )
  } catch {
    // Ignore errors - the function might not be ready yet
  }

  // Wait for the function host to initialize
  logger.info('Waiting for function host to initialize...')
  await delay(30000)

  // Poll for the webpubsub_extension key
  const maxRetries = 30
  const retryInterval = 10000
  let key: string | undefined

  for (let i = 1; i <= maxRetries; i++) {
    try {
      const result = await exec(
        `az functionapp keys list --name "${functionAppName}" --resource-group "${resourceGroupName}" --query "systemKeys.webpubsub_extension" -o tsv`
      )
      const trimmedKey = result.stdout.trim()
      if (trimmedKey && trimmedKey !== 'null' && trimmedKey !== '') {
        key = trimmedKey
        logger.info(`webpubsub_extension key found after ${i} attempts`)
        break
      }
    } catch {
      // Ignore errors
    }

    // Re-trigger function app every 5 attempts
    if (i % 5 === 0) {
      logger.info('Re-triggering function host...')
      try {
        await exec(
          `curl -s -o /dev/null "https://${functionAppName}.azurewebsites.net/api/graphql" -X POST -H "Content-Type: application/json" -d '{"query":"{ __typename }"}'`
        )
      } catch {
        // Ignore errors
      }
    }

    logger.info(`Attempt ${i}/${maxRetries}: Key not available yet, waiting...`)
    await delay(retryInterval)
  }

  if (!key) {
    logger.warn('webpubsub_extension key not found. Web PubSub subscriptions may not work.')
    logger.warn('You can manually update the hub by running:')
    logger.warn(
      `  KEY=$(az functionapp keys list --name ${functionAppName} --resource-group ${resourceGroupName} --query 'systemKeys.webpubsub_extension' -o tsv)`
    )
    logger.warn(
      `  az webpubsub hub update --name ${webPubSubName} --resource-group ${resourceGroupName} --hub-name ${hubName} --event-handler url-template="https://${functionAppName}.azurewebsites.net/runtime/webhooks/webpubsub?code=$KEY" user-event-pattern="*" system-event="connect" system-event="disconnected"`
    )
    return
  }

  // Update the hub with the correct key
  logger.info('Updating Web PubSub hub...')
  try {
    await exec(
      `az webpubsub hub update --name "${webPubSubName}" --resource-group "${resourceGroupName}" --hub-name "${hubName}" --event-handler url-template="https://${functionAppName}.azurewebsites.net/runtime/webhooks/webpubsub?code=${key}" user-event-pattern="*" system-event="connect" system-event="disconnected"`
    )
    logger.info('Web PubSub hub updated successfully')
  } catch (error) {
    logger.error('Failed to update Web PubSub hub:', error)
    throw error
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
  await resourceManagementClient.resourceGroups.beginDeleteAndWait(
    createResourceGroupName(config.appName, config.environmentName)
  )
}
