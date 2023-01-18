import { BoosterConfig } from '@boostercloud/framework-types'
import { SubscriptionsNotifierFunctionDefinition } from '../types/functionDefinition'

export class SubscriptionsNotifierFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): Array<SubscriptionsNotifierFunctionDefinition> | undefined {
    return Object.keys(this.config.readModels).map((readModel) => {
      return {
        name: `${readModel}-subscriptions-notifier`,
        config: {
          bindings: [
            {
              type: 'cosmosDBTrigger',
              name: 'rawEvent',
              direction: 'in',
              leaseCollectionName: `leases-${readModel}`,
              connectionStringSetting: 'COSMOSDB_CONNECTION_STRING',
              databaseName: this.config.resourceNames.applicationStack,
              collectionName: this.config.resourceNames.forReadModel(readModel),
              createLeaseCollectionIfNotExists: 'true',
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
