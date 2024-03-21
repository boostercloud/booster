import { BoosterConfig } from '@boostercloud/framework-types'
import { SubscriptionsNotifierFunctionDefinition } from '../types/functionDefinition'

export class SubscriptionsNotifierFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): Array<SubscriptionsNotifierFunctionDefinition> {
    return Object.keys(this.config.readModels).map((readModel) => {
      return {
        name: `${readModel}-subscriptions-notifier`,
        config: {
          bindings: [
            {
              type: 'cosmosDBTrigger',
              name: 'rawEvent',
              direction: 'in',
              leaseContainerName: `leases-${readModel}`,
              connection: 'COSMOSDB_CONNECTION_STRING',
              databaseName: this.config.resourceNames.applicationStack,
              containerName: this.config.resourceNames.forReadModel(readModel),
              createLeaseContainerIfNotExists: 'true',
            },
            {
              type: 'webPubSubConnection',
              name: 'connection',
              hub: 'booster',
              direction: 'out',
            },
          ],
          scriptFile: this.config.functionRelativePath,
          entryPoint: this.config.notifySubscribersHandler.split('.')[1],
        },
      }
    })
  }
}
