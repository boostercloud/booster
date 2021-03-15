/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  Logger,
  ProviderInfrastructure,
  ProviderLibrary,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import { EventRegistry } from './services/event-registry'
import * as EventsAdapter from './library/events-adapter'
import * as ReadModelAdapter from './library/read-model-adapter'
import { ReadModelRegistry } from './services/read-model-registry'

const storageUrl = 'http://localhost:3500'
const eventRegistry = new EventRegistry(storageUrl)
const readModelRegistry = new ReadModelRegistry(storageUrl)
// const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))

export const Provider = (): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: EventsAdapter.rawToEnvelopes,
    store: EventsAdapter.store.bind(null, eventRegistry),
    forEntitySince: EventsAdapter.forEntitySince.bind(null, eventRegistry),
    latestEntitySnapshot: EventsAdapter.latestEntitySnapshot.bind(null, eventRegistry),
    search: EventsAdapter.search.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    rawToEnvelopes: (config: any, logger: any, rawEvents: any) => {
      console.log('rawToEnvelopes called')
      return new Promise(() => {})
    },
    fetch: (config: any, logger: any, readModelName: any, readModelID: any) => {
      console.log('fetch called')
      return new Promise(() => {})
    },
    search: async <TReadModel extends ReadModelInterface>(
      config: BoosterConfig,
      logger: Logger,
      readModelName: string,
      filters: any
    ) =>
      (await ReadModelAdapter.search(readModelRegistry, config, logger, readModelName, filters)) as Array<TReadModel>,
    store: ReadModelAdapter.store.bind(null, readModelRegistry),
    delete: ReadModelAdapter.deleteReadModel.bind(null, readModelRegistry),
    subscribe: ReadModelAdapter.subscribe,
    fetchSubscriptions: ReadModelAdapter.fetchSubscriptions,
    deleteSubscription: ReadModelAdapter.deleteSubscription,
    deleteAllSubscriptions: ReadModelAdapter.deleteAllSubscriptions,
  },
  // ProviderGraphQLLibrary
  graphQL: {
    rawToEnvelope: async (request: { body: any }, logger: { debug: (arg0: string, arg1: any) => void }) => {
      logger.debug('Received GraphQL request: ', request)
      let graphQLValue = undefined
      if (request.body) {
        graphQLValue = request.body
      }

      return {
        requestID: '1',
        eventType: 'MESSAGE',
        currentUser: JSON.parse('{}'),
        value: graphQLValue,
      }
    },
    handleResult: async (result: unknown, headers: Record<string, string> | undefined) => {
      return {
        headers: {
          'Access-Control-Allow-Origin': '*',
          ...headers,
        },
        statusCode: 200,
        body: result ? JSON.stringify(result) : '',
      }
    },
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: (_rawMessage: any) => {
      return { email: '', role: '', username: '' }
    },
    fromAuthToken: (token: any) => {
      return new Promise(() => {})
    },
    handleSignUpResult: (boosterConfir: any, request: any, userEnvelope: any) => {},
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded: (body: any) => {
      console.log('request Succeeded')
      return new Promise(() => {})
    },
    requestFailed: (error: any) => {
      console.log('request Failed')
      return new Promise(() => {})
    },
  },
  connections: {
    storeData: (_boosterConfig: any, _connectionId: any, _data: any) => {
      return new Promise(() => {})
    },
    fetchData: (boosterConfig: any, connectionId: any) => {
      return new Promise(() => {})
    },
    deleteData: (boosterConfig: any, connectionId: any) => {
      return new Promise(() => {})
    },
    sendMessage: (boosterConfig: any, connectionId: any, data: any) => {
      return new Promise(() => {})
    },
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: (rawMessage: any, logger: any) => {
      return new Promise(() => {})
    },
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
})
