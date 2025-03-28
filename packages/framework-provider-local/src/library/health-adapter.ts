import { EventRegistry, ReadModelRegistry } from '../services'
import { eventsDatabase, readModelsDatabase } from '../paths'
import { boosterLocalPort, HealthEnvelope, UUID } from '@boostercloud/framework-types'
import { existsSync } from 'fs'
import * as express from 'express'
import { request } from '@boostercloud/framework-common-helpers'
import Nedb from '@seald-io/nedb'

export async function databaseUrl(): Promise<Array<string>> {
  return [eventsDatabase, readModelsDatabase]
}

export async function countAll(database: Nedb): Promise<number> {
  await database.loadDatabaseAsync()
  const count = await database.countAsync({})
  return count ?? 0
}

export async function databaseEventsHealthDetails(eventRegistry: EventRegistry): Promise<unknown> {
  const count = await countAll(eventRegistry.events)
  return {
    file: eventsDatabase,
    count: count,
  }
}

export async function graphqlFunctionUrl(): Promise<string> {
  try {
    const port = boosterLocalPort()
    return `http://localhost:${port}/graphql`
  } catch (e) {
    return ''
  }
}

export async function isDatabaseEventUp(): Promise<boolean> {
  return existsSync(eventsDatabase)
}

export async function areDatabaseReadModelsUp(): Promise<boolean> {
  return existsSync(readModelsDatabase)
}

export async function isGraphQLFunctionUp(): Promise<boolean> {
  try {
    const url = await graphqlFunctionUrl()
    const response = await request(
      url,
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

function rawRequestToSensorHealthComponentPath(rawRequest: express.Request): string {
  const url = rawRequest?.url
  if (url && url !== '/') {
    return url.substring(1)
  }
  return ''
}

export function rawRequestToSensorHealth(rawRequest: express.Request): HealthEnvelope {
  const componentPath = rawRequestToSensorHealthComponentPath(rawRequest)
  const requestID = UUID.generate()
  const headers = rawRequest.headers
  return {
    requestID: requestID,
    context: {
      request: {
        headers: headers,
        body: {},
      },
      rawContext: rawRequest,
    },
    componentPath: componentPath,
    token: headers?.authorization,
  }
}

export async function databaseReadModelsHealthDetails(readModelRegistry: ReadModelRegistry): Promise<unknown> {
  const count = await countAll(readModelRegistry.readModels)
  return {
    file: readModelsDatabase,
    count: count,
  }
}
