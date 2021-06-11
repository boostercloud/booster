import { ReadModelRegistry } from '@boostercloud/framework-provider-local'
import { readModelDbFilename } from './helpers'

export class LocalCounters {
  constructor(private readonly stackName: string) {}

  public async subscriptions(): Promise<number> {
    return this.countTableItems(`${this.stackName}-subscriptions-store`)
  }

  public async connections(): Promise<number> {
    return this.countTableItems(`${this.stackName}-connections-store`)
  }

  public async events(): Promise<number> {
    //TODO implement method
    return -1
  }

  public async readModels(readModelName: string): Promise<number> {
    const readModelRegistry = new ReadModelRegistry(readModelDbFilename())
    const result = await readModelRegistry.count(readModelName)
    return result
  }

  private async countTableItems(tableName: string): Promise<number> {
    //TODO implement or remove method
    return -1
  }
}
