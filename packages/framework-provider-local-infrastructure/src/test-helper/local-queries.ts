import { ReadModelEnvelope } from '@boostercloud/framework-types';
import { ReadModelRegistry } from '@boostercloud/framework-provider-local'
import { readModelDbFilename } from './helpers'

export class LocalQueries {
  constructor() {}

  public async events(primaryKey: string, latestFirst = true): Promise<Array<unknown>> {
    // TODO implement method
    return []
  }

  public async readModels(primaryKey: string, readModelName: string, latestFirst = true): Promise<Array<unknown>> {
    const readModelRegistry = new ReadModelRegistry(readModelDbFilename())
    const result: ReadModelEnvelope[] = await readModelRegistry.query({
      'value.id': primaryKey,
      typeName: readModelName,
    })
    return result
  }
}
