import { CosmosClient } from '@azure/cosmos'

export class AzureCounters {
  appName: string
  db: CosmosClient
  constructor(appName: string, cosmosConnectionString: string) {
    this.appName = appName
    this.db = new CosmosClient(cosmosConnectionString)
  }
  public async subscriptions(): Promise<number> {
    return this.countTableItems('subscriptions-store')
  }

  public async connections(): Promise<number> {
    return this.countTableItems('connections-store')
  }

  public async events(): Promise<number> {
    return await this.itemsCount(
      `${this.appName}-app-events-store`,
      'SELECT COUNT(1) as total FROM c WHERE c.kind="event"'
    )
  }

  public async readModels(readModelName: string): Promise<number> {
    return await this.itemsCount(`${this.appName}-app-${readModelName}`, 'SELECT COUNT(1) as total FROM c')
  }

  private async itemsCount(table: string, query: string): Promise<number> {
    const { resources } = await this.db.database(`${this.appName}-app`).container(table).items.query(query).fetchAll()
    return resources[0]?.total
  }

  private async countTableItems(tableName: string): Promise<number> {
    return this.itemsCount(`${this.appName}-app-${tableName}`, 'SELECT COUNT(1) as total FROM c') ?? -1
  }
}
