import { BoosterConfig } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'

export class ReadModelsStack {
  public constructor(readonly config: BoosterConfig, private readonly cosmosDbConnectionString: string) {}

  public async build(): Promise<void> {
    const cosmosClient = new CosmosClient(this.cosmosDbConnectionString)

    // containers for read models
    const readModelContainers = Object.keys(this.config.readModels).map((readModelName) => {
      return cosmosClient.database(this.config.resourceNames.applicationStack).containers.createIfNotExists({
        id: this.config.resourceNames.forReadModel(readModelName),
        partitionKey: '/id',
      })
    })

    await Promise.all(readModelContainers)
  }
}
