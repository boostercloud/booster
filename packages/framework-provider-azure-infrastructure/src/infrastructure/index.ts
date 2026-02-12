import { BoosterConfig } from '@boostercloud/framework-types'
import {
  azureCredentials,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createResourceManagementClient,
  createStreamFunctionResourceGroupName,
  createWebPubSubManagementClient,
  createWebSiteManagementClient,
} from './helper/utils'
import { getLogger, runCommand } from '@boostercloud/framework-common-helpers'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { ApplicationBuilder } from './application-builder'
import { RocketBuilder } from './rockets/rocket-builder'
import { FunctionZip } from './helper/function-zip'

const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms))

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
 * Triggers a cold start on the function app by sending a lightweight GraphQL request.
 * Errors are ignored since the function may not be ready yet.
 * @param functionAppName - The name of the function app to trigger
 */
async function triggerColdStart(functionAppName: string): Promise<void> {
  try {
    await fetch(`https://${functionAppName}.azurewebsites.net/api/graphql`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: '{ __typename }' }),
      signal: AbortSignal.timeout(10000),
    })
  } catch {
    // Ignore errors - the function might not be ready yet
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

  const credentials = await azureCredentials()
  const websiteClient = await createWebSiteManagementClient(credentials)
  const webPubSubClient = await createWebPubSubManagementClient(credentials)

  // First, trigger the function app to start by hitting an endpoint
  logger.info('Triggering function app cold start...')
  await triggerColdStart(functionAppName)

  // Wait for the function host to initialize
  logger.info('Waiting for function host to initialize...')
  await delay(30000)

  // Poll for the webpubsub_extension key
  const maxRetries = 30
  const retryInterval = 10000
  let key: string | undefined

  for (let i = 1; i <= maxRetries; i++) {
    try {
      const hostKeys = await websiteClient.webApps.listHostKeys(resourceGroupName, functionAppName)
      const extensionKey = hostKeys.systemKeys?.['webpubsub_extension']
      if (extensionKey) {
        key = extensionKey
        logger.info(`webpubsub_extension key found after ${i} attempts`)
        break
      }
    } catch {
      // Ignore errors
    }

    // Re-trigger function app every 5 attempts
    if (i % 5 === 0) {
      logger.info('Re-triggering function host...')
      await triggerColdStart(functionAppName)
    }

    logger.info(`Attempt ${i}/${maxRetries}: Key not available yet, waiting...`)
    await delay(retryInterval)
  }

  if (!key) {
    const errorMessage =
      'webpubsub_extension key not found after waiting for function host initialization. ' +
      'Web PubSub subscriptions will not work. ' +
      `You can manually retrieve the key from: Azure Portal > Function App "${functionAppName}" > App keys > System keys > webpubsub_extension`
    logger.error(errorMessage)
    throw new Error(errorMessage)
  }

  // Update the hub with the correct key
  logger.info('Updating Web PubSub hub...')
  try {
    await webPubSubClient.webPubSubHubs.beginCreateOrUpdateAndWait(hubName, resourceGroupName, webPubSubName, {
      properties: {
        eventHandlers: [
          {
            urlTemplate: `https://${functionAppName}.azurewebsites.net/runtime/webhooks/webpubsub?code=${key}`,
            userEventPattern: '*',
            systemEvents: ['connect', 'disconnected'],
          },
        ],
      },
    })
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
