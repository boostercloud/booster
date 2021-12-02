export class AzureQueries {
  constructor() {}

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public async events(primaryKey: string, _latestFirst = true): Promise<Array<unknown>> {
    return [] //TODO We should implement this for provider unaware functionality tests
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public async readModels(primaryKey: string, readModelName: string, _latestFirst = true): Promise<Array<unknown>> {
    return [] //TODO We should implement this for provider unaware functionality tests
  }
}
