import { BoosterConfig } from '@boostercloud/framework-types'
import { EventHubInputBinding, EventStreamConsumerHandlerFunctionDefinition } from '../types/functionDefinition'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'

/**
 * Function to consume EventHub events
 */
export class EventStreamConsumerFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): EventStreamConsumerHandlerFunctionDefinition {
    const binding: EventHubInputBinding = {
      type: 'eventHubTrigger',
      name: 'eventHubMessages',
      direction: 'in',
      eventHubName: this.config.resourceNames.streamTopic,
      connection: environmentVarNames.eventHubConnectionString,
      cardinality: 'many',
      consumerGroup: '$Default',
      dataType: 'string',
    }
    return {
      name: 'eventHandler',
      config: {
        bindings: [binding],
        scriptFile: this.config.functionRelativePath,
        entryPoint: this.config.eventStreamConsumer.split('.')[1],
      },
    }
  }
}
