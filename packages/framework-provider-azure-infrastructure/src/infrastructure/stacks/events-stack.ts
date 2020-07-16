import { BoosterConfig } from '@boostercloud/framework-types'
import { eventsStoreAttributes } from '@boostercloud/framework-provider-azure'
import { CosmosClient } from '@azure/cosmos'

export class EventsStack {
  public constructor(readonly config: BoosterConfig, private readonly cosmosDbConnectionString: string) {}

  public async build(): Promise<void> {
    const cosmosClient = new CosmosClient(this.cosmosDbConnectionString)

    // container for event store
    await cosmosClient.database(this.config.resourceNames.applicationStack).containers.createIfNotExists({
      id: this.config.resourceNames.eventsStore,
      partitionKey: `/${eventsStoreAttributes.partitionKey}`,
    })
  }
}
