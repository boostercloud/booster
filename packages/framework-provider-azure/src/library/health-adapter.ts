import { BoosterConfig, HealthEnvelope } from '@boostercloud/framework-types'
import { Container, CosmosClient } from '@azure/cosmos'
import { environmentVarNames } from '../constants'
import { Context } from '@azure/functions'
import { request } from '@boostercloud/framework-common-helpers'

export async function databaseUrl(cosmosDb: CosmosClient, config: BoosterConfig): Promise<Array<string>> {
  const database = cosmosDb.database(config.resourceNames.applicationStack)
  return [database.url]
}

export function getContainer(cosmosDb: CosmosClient, config: BoosterConfig, containerName: string): Container {
  return cosmosDb.database(config.resourceNames.applicationStack).container(containerName)
}

export async function isContainerUp(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  containerName: string
): Promise<boolean> {
  const container = getContainer(cosmosDb, config, containerName)
  const { resources } = await container.items.query('SELECT TOP 1 1 FROM c', { maxItemCount: -1 }).fetchAll()
  return resources !== undefined
}

export async function countAll(container: Container): Promise<number> {
  const { resources } = await container.items.query('SELECT VALUE COUNT(1) FROM c', { maxItemCount: -1 }).fetchAll()
  return resources ? resources[0] : 0
}

export async function databaseEventsHealthDetails(cosmosDb: CosmosClient, config: BoosterConfig): Promise<unknown> {
  const container = getContainer(cosmosDb, config, config.resourceNames.eventsStore)
  const url = container.url
  const count = await countAll(container)
  return {
    url: url,
    count: count,
  }
}

export async function graphqlFunctionUrl(): Promise<string> {
  try {
    const basePath = process.env[environmentVarNames.restAPIURL]
    return `${basePath}/graphql`
  } catch (e) {
    return ''
  }
}

export async function isDatabaseEventUp(cosmosDb: CosmosClient, config: BoosterConfig): Promise<boolean> {
  return await isContainerUp(cosmosDb, config, config.resourceNames.eventsStore)
}

export async function areDatabaseReadModelsUp(cosmosDb: CosmosClient, config: BoosterConfig): Promise<boolean> {
  const promises = Object.values(config.readModels).map((readModel) => {
    const name = readModel.class.name
    const container = config.resourceNames.forReadModel(name)
    return isContainerUp(cosmosDb, config, container)
  })
  const containersUp = await Promise.all(promises)
  return containersUp.every((isContainerUp) => isContainerUp)
}

export async function isGraphQLFunctionUp(): Promise<boolean> {
  try {
    const restAPIUrl = await graphqlFunctionUrl()
    const response = await request(restAPIUrl, 'POST')
    return response.status === 200
  } catch (e) {
    return false
  }
}

export function rawRequestToSensorHealthComponentPath(rawRequest: Context): string {
  const parameters = rawRequest.req?.url.replace(/^.*sensor\/health\/?/, '')
  return parameters ?? ''
}

export function rawRequestToSensorHealth(context: Context): HealthEnvelope {
  const componentPath = rawRequestToSensorHealthComponentPath(context)
  const requestID = context.executionContext.invocationId
  return {
    requestID: requestID,
    context: {
      request: {
        headers: context.req?.headers,
        body: context.req?.body,
      },
      rawContext: context,
    },
    componentPath: componentPath,
    token: context.req?.headers?.authorization,
  }
}

export async function databaseReadModelsHealthDetails(cosmosDb: CosmosClient, config: BoosterConfig): Promise<unknown> {
  const readModels = Object.values(config.readModels)
  const result: Array<unknown> = []
  for (const readModel of readModels) {
    const name = readModel.class.name
    const containerName = config.resourceNames.forReadModel(name)
    const container = getContainer(cosmosDb, config, containerName)
    const url: string = container.url
    const count: number = await countAll(container)
    result.push({
      url: url,
      count: count,
    })
  }
  return result
}
