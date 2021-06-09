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
    return this.countTableItems(`${this.stackName}-${readModelName}`)
  }

  private async countTableItems(tableName: string): Promise<number> {
    //TODO implement or remove method
    return -1
  }
}
