import { CosmosClient } from '@azure/cosmos'

export class AzureQueries {
  appName: string
  db: CosmosClient
  constructor(appName: string, cosmosConnectionString: string) {
    this.appName = appName
    this.db = new CosmosClient(cosmosConnectionString)
  }

  public async events(primaryKey: string): Promise<Array<unknown>> {
    const { resources } = await this.db
      .database(`${this.appName}-app`)
      .container(`${this.appName}-app-events-store`)
      .items.query({
        query: 'SELECT * from c WHERE c.entityTypeName_entityID_kind = @eventId order by c.createdAt DESC',
        parameters: [{ name: '@eventId', value: primaryKey }],
      })
      .fetchAll()
    return resources
  }

  public async readModels(primaryKey: string, readModelName: string): Promise<Array<unknown>> {
    const { resource } = await this.db
      .database(`${this.appName}-app`)
      .container(`${this.appName}-app-${readModelName}`)
      .item(primaryKey as string, primaryKey)
      .read()
    return [resource]
  }
}
