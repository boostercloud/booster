import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import webSiteManagement from 'azure-arm-website'
import { buildResource, packageAzureFunction, deployFunctionPackage } from '../utils'
import { ApiStack } from './api-stack'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { DeploymentExtended } from 'azure-arm-resource/lib/resource/models'
import { armTemplates } from '../arm-templates'
import { GraphqlFunction } from './graphql-function'
import { EventHandlerFunction } from './event-handler-function'
import { SchedulesFunctions } from './schedules-functions'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig) {}

  public async buildOn(
    logger: Logger,
    resourceManagementClient: ResourceManagementClient,
    webSiteManagementClient: webSiteManagement,
    resourceGroupName: string
  ): Promise<void> {
    logger.info('Creating Storage and Cosmos DB accounts...')
    const accountCreationResults: Array<DeploymentExtended> = await Promise.all([
      buildResource(resourceManagementClient, resourceGroupName, {}, armTemplates.storageAccount),
      buildResource(
        resourceManagementClient,
        resourceGroupName,
        {
          databaseName: { value: this.config.resourceNames.applicationStack },
        },
        armTemplates.cosmosDbAccount
      ),
    ])

    const storageAccountName = accountCreationResults[0].properties?.outputs.storageAccountName.value
    const cosmosDbConnectionString = accountCreationResults[1].properties?.outputs.connectionString.value

    logger.info('Creating Function App...')
    const functionAppDeployment = await buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        storageAccountName: {
          value: storageAccountName,
        },
      },
      armTemplates.functionApp
    )

    const apiStack = new ApiStack(
      this.config,
      resourceManagementClient,
      resourceGroupName,
      functionAppDeployment.properties?.outputs.functionAppName.value
    )
    const eventsStack = new EventsStack(this.config, cosmosDbConnectionString)
    const readModelsStack = new ReadModelsStack(this.config, cosmosDbConnectionString)

    logger.info('Creating API Management Service and Cosmos DB containers...')
    const buildResults = await Promise.all([
      apiStack.build(),
      eventsStack.build(),
      readModelsStack.build(),
      webSiteManagementClient.webApps.listApplicationSettings(
        resourceGroupName,
        functionAppDeployment.properties?.outputs.functionAppName.value
      ),
      webSiteManagementClient.webApps.listPublishingCredentials(
        resourceGroupName,
        functionAppDeployment.properties?.outputs.functionAppName.value
      ),
    ])

    // get Function App settings and API Management Service name
    const apiManagementServiceName = buildResults[0]
    const appSettings = buildResults[3]
    const credentials = buildResults[4]

    // add new app settings with the Booster environment variables and Cosmos DB connection string
    appSettings.properties = {
      ...appSettings.properties,
      BOOSTER_ENV: this.config.environmentName,
      BOOSTER_REST_API_URL: `https://${apiManagementServiceName}.azure-api.net/${this.config.environmentName}`,
      COSMOSDB_CONNECTION_STRING: cosmosDbConnectionString,
    }

    // update app settings
    await webSiteManagementClient.webApps.updateApplicationSettings(
      resourceGroupName,
      functionAppDeployment.properties?.outputs.functionAppName.value,
      {
        properties: appSettings.properties,
      }
    )

    logger.info('Packaging Booster project for deployment...')
    const graphqlFunctionDefinition = new GraphqlFunction(this.config).getFunctionDefinition()
    const eventHandlerFunctionDefinition = new EventHandlerFunction(this.config).getFunctionDefinition()
    let featuresDefinitions = [graphqlFunctionDefinition, eventHandlerFunctionDefinition]
    const schedulesFunctionsDefinition = new SchedulesFunctions(this.config).getFunctionDefinitions()
    if (schedulesFunctionsDefinition) {
      featuresDefinitions = featuresDefinitions.concat(schedulesFunctionsDefinition)
    }
    const zipPath = await packageAzureFunction(featuresDefinitions)

    logger.info('Deploying Zip...')
    await deployFunctionPackage(
      zipPath,
      credentials.publishingUserName,
      credentials.publishingPassword ?? '',
      credentials.name ?? ''
    )

    logger.info(`Deployed environment: ${appSettings.properties.BOOSTER_ENV}`)
    logger.info(`REST API base URL: ${appSettings.properties.BOOSTER_REST_API_URL}`)
  }
}
