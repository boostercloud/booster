import { BoosterConfig } from '@boostercloud/framework-types'
import {
  EventHandlerBinding,
  EventHubOutBinding,
  EventStreamProducerHandlerFunctionDefinition,
} from '../types/functionDefinition'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'

/**
 * Function to consume CosmosDB changes and produce EventHub events
 */
export class EventStreamProducerFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): EventStreamProducerHandlerFunctionDefinition {
    const cosmosBinding: EventHandlerBinding = {
      type: 'cosmosDBTrigger',
      name: 'rawEvent',
      direction: 'in',
      leaseContainerName: 'leases',
      connection: 'COSMOSDB_CONNECTION_STRING',
      databaseName: this.config.resourceNames.applicationStack,
      containerName: this.config.resourceNames.eventsStore,
      createLeaseContainerIfNotExists: 'true',
    }
    const eventHubBinding: EventHubOutBinding = {
      name: 'eventHubMessages',
      direction: 'out',
      type: 'eventHub',
      connection: environmentVarNames.eventHubConnectionString,
      eventHubName: this.config.resourceNames.streamTopic,
    }
    return {
      name: 'eventProducer',
      config: {
        bindings: [cosmosBinding, eventHubBinding],
        scriptFile: this.config.functionRelativePath,
        entryPoint: this.config.eventStreamProducer.split('.')[1],
      },
    }
  }
}
