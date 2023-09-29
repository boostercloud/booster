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
            leaseContainerName: 'leases',
            connection: 'COSMOSDB_CONNECTION_STRING',
            databaseName: this.config.resourceNames.applicationStack,
            containerName: this.config.resourceNames.eventsStore,
            createLeaseContainerIfNotExists: 'true',
          },
        ],
        scriptFile: this.config.functionRelativePath,
        entryPoint: this.config.eventDispatcherHandler.split('.')[1],
      },
    }
  }
}
