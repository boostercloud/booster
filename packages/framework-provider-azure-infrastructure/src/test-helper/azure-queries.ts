import { CosmosClient } from '@azure/cosmos'

export class AzureQueries {
  resourceGroupName: string
  db: CosmosClient
  constructor(resourceGroupName: string, cosmosConnectionString: string) {
    this.resourceGroupName = resourceGroupName
    this.db = new CosmosClient(cosmosConnectionString)
  }

  public async events(primaryKey: string, _latestFirst = true): Promise<Array<unknown>> {
    return await this.fetchFromCosmos('event-store', primaryKey)
  }

  public async readModels(primaryKey: string, readModelName: string, _latestFirst = true): Promise<Array<unknown>> {
    return await this.fetchFromCosmos(readModelName, primaryKey)
  }

  private async fetchFromCosmos(readModelName: string, primaryKey: string) {
    const { resource } = await this.db
      .database(this.resourceGroupName)
      .container(`${this.resourceGroupName}-${readModelName}`)
      .item(primaryKey as string, primaryKey)
      .read()
    return resource
  }
}
