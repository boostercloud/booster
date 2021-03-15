/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  EventEnvelope,
  Logger,
  ProviderInfrastructure,
  ProviderLibrary,
  UserApp,
} from '@boostercloud/framework-types'
import { EventRegistry } from './services/event-registry'
import * as EventsAdapter from './library/events-adapter'
import * as path from 'path'
import * as fetch from 'node-fetch'

const eventRegistry = new EventRegistry('http://localhost:3500')
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))

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
    search: async (_config: any, _logger: any, _entityTypeName: any, filters: { id: { values: any[] } }) => {
      if (filters.id.values[0]) {
        const result = await fetch('http://localhost:3500/v1.0/state/statestore/' + filters.id.values[0])
        try {
          const response = await result.json()
          if (response) {
            response.id = filters.id.values[0]
          }
          return [response]
        } catch (err) {
          return []
        }
      } else {
        return []
      }
    },
    store: (config: any, logger: any, readModelName: any, readModel: any) => {
      console.log('readmodels: store')
      return new Promise(() => {})
    },
    delete: (config: any, logger: any, readModelName: any, readModel: any) => {
      console.log('readmodelsL delete')
      return new Promise(() => {})
    },
    subscribe: (config: any, logger: any, subscriptionEnvelope: any) => {
      console.log('subscribe called')
      return new Promise(() => {})
    },
    fetchSubscriptions: (config: any, logger: any, subscriptionName: any) => {
      console.log('fetchSubscription called')
      return new Promise(() => {})
    },
    deleteSubscription: () => {
      return new Promise(() => {})
    },
    deleteAllSubscriptions: () => {
      return new Promise(() => {})
    },
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
    handleResult: (result: any, headers: any) => {
      return new Promise(() => {})
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
