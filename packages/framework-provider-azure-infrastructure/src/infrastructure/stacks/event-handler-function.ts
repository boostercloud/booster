import { BoosterConfig } from '@boostercloud/framework-types'
import { EventHandlerFunctionDefinition } from '../types/functionDefinition'

export class EventHandlerFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): EventHandlerFunctionDefinition {
    return {
      name: 'eventHandler',
      config: {
        bindings: [
          {
            type: 'cosmosDBTrigger',
            name: 'rawEvent',
            direction: 'in',
            leaseCollectionName: 'leases',
            connectionStringSetting: 'COSMOSDB_CONNECTION_STRING',
            databaseName: this.config.resourceNames.applicationStack,
            collectionName: this.config.resourceNames.eventsStore,
            createLeaseCollectionIfNotExists: 'true',
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: this.config.eventDispatcherHandler.split('.')[1],
      },
    }
  }
}
