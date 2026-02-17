import { BoosterConfig, HealthEnvelope } from '@boostercloud/framework-types'
import { Container, CosmosClient } from '@azure/cosmos'
import { environmentVarNames } from '../constants'
import { request } from '@boostercloud/framework-common-helpers'
import { AzureHttpFunctionInput, isHttpFunctionInput } from '../types/azure-func-types'

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
  const { resources } = await container.items
    .query({
      query: 'SELECT TOP 1 1 FROM c',
      parameters: [],
    })
    .fetchAll()
  return resources !== undefined
}

export async function countAll(container: Container): Promise<number> {
  const { resources } = await container.items
    .query({
      query: 'SELECT VALUE COUNT(1) FROM c',
      parameters: [],
    })
    .fetchAll()
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

export async function rocketFunctionAppUrl(functionAppName: string): Promise<string> {
  return `https://${functionAppName}.azurewebsites.net`
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
    const response = await request(
      restAPIUrl,
      'POST',
      JSON.stringify({
        query: 'query { __typename }',
      })
    )
    return response.status === 200
  } catch (e) {
    return false
  }
}

export async function isRocketFunctionUp(rocketFunctionAppName: string): Promise<boolean> {
  try {
    const functionAppUrl = await rocketFunctionAppUrl(rocketFunctionAppName)
    const response = await request(functionAppUrl, 'GET')
    return response.status === 200
  } catch (e) {
    return false
  }
}

export async function areRocketFunctionsUp(): Promise<{ [key: string]: boolean }> {
  const mappingString = process.env[environmentVarNames.rocketPackageMapping] || ''
  const rocketPackageMapping = mappingString
    .split(';')
    .filter(Boolean)
    .reduce((acc, pair) => {
      const [pkg, func] = pair.split(':')
      if (pkg && func) {
        acc[pkg] = func
      }
      return acc
    }, {} as Record<string, string>)
  const results = await Promise.all(
    Object.entries(rocketPackageMapping).map(async ([packageName, functionAppName]) => {
      const isUp = await isRocketFunctionUp(functionAppName)
      return { [packageName]: isUp }
    })
  )

  return results.reduce((acc, result) => ({ ...acc, ...result }), {})
}

export function rawRequestToSensorHealthComponentPath(rawRequest: unknown): string {
  if (isHttpFunctionInput(rawRequest)) {
    const input = rawRequest as AzureHttpFunctionInput
    const url = input.request.url
    const parameters = url.replace(/^.*sensor\/health\/?/, '')
    return parameters ?? ''
  }
  return ''
}

/**
 * Converts the raw HTTP request to a HealthEnvelope.
 * Note: Body is not parsed synchronously in v4 - it will be undefined.
 * The health endpoint typically doesn't need the request body.
 * @param rawRequest - The raw HTTP request from the Azure Function
 * @returns A HealthEnvelope object
 */
export function rawRequestToSensorHealth(rawRequest: unknown): HealthEnvelope {
  if (!isHttpFunctionInput(rawRequest)) {
    throw new Error('Invalid input type for rawRequestToSensorHealth: expected AzureHttpFunctionInput')
  }

  const input = rawRequest as AzureHttpFunctionInput
  const { request, context } = input
  const componentPath = rawRequestToSensorHealthComponentPath(rawRequest)
  const requestID = context.invocationId

  // Convert headers to a plain object
  const headers: Record<string, string> = {}
  request.headers.forEach((value, key) => {
    headers[key] = value
  })

  return {
    requestID: requestID,
    context: {
      request: {
        headers,
        body: undefined, // Body not parsed synchronously in v4
      },
      rawContext: rawRequest,
    },
    componentPath: componentPath,
    token: request.headers.get('authorization') ?? undefined,
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
