import { BoosterConfig } from '@boostercloud/framework-types'
import { EventHandlerBinding, EventHandlerFunctionDefinition } from '../types/functionDefinition'

/**
 * This function will listen to CosmosDB changes
 */
export class EventHandlerFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): EventHandlerFunctionDefinition {
    const eventHandlerBinding: EventHandlerBinding = {
      type: 'cosmosDBTrigger',
      name: 'rawEvent',
      direction: 'in',
      leaseContainerName: 'leases',
      connection: 'COSMOSDB_CONNECTION_STRING',
      databaseName: this.config.resourceNames.applicationStack,
      containerName: this.config.resourceNames.eventsStore,
      createLeaseContainerIfNotExists: 'true',
    }
    return {
      name: 'eventHandler',
      config: {
        bindings: [eventHandlerBinding],
        scriptFile: this.config.functionRelativePath,
        entryPoint: this.config.eventDispatcherHandler.split('.')[1],
      },
    }
  }
}
