import { BoosterConfig, Logger, ReadModelInterface, ReadModelEnvelope, UUID } from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { EntityType } from '../types/query'
import { DatabaseAdapter } from './database-adapter'

// TODO: Implement querying with filters properly
interface Filters {
  id: {
    eq: string
  }
}

export class ReadModelRegistry {
  private readonly databaseAdapter: DatabaseAdapter

  constructor(readonly url: string) {
    this.databaseAdapter = new DatabaseAdapter()
  }

  public async search(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    filters: Filters
  ): Promise<Array<ReadModelInterface>> {
    const l = (msg: string): void => logger.debug('readModelRegistry#search: ' + msg)
    if (filters.id) {
      // TODO: Allow complex filters in K8s provider. Currently we only allow filter by Id or get all entries
      const readModelId = filters.id.eq
      l(`Got id ${readModelId ?? 'UNDEFINED'}`)
      if (!readModelId) throw new Error('Only searching by ID is supported')
      const url = `${this.url}/v1.0/state/statestore/` // ${this.readModelKey(readModelName, readModelId)}`
      l(`Performing a fetch to ${url}`)
      const response = await fetch(url)
      if (!response.ok) {
        l(`Error on performing GET from ${url}`)
      }
      l(`Got result ${JSON.stringify(response)}`)
      try {
        const body = await response.json()
        l(`Got JSON ${JSON.stringify(body)}`)
        // TODO: Remove unnecessary id setting from k8s read model registry
        if (body) {
          body.id = readModelId
        }
        return [body.value]
      } catch (err) {
        l(`Error ${JSON.stringify(err)}`)
        return []
      }
    } else {
      const query = {
        type: EntityType.ReadModel,
        typeName: readModelName,
        id: filters.id,
        keyPredicate: () => true,
        valuePredicate: () => true,
        sortBy: () => 1,
      }
      const keys = (await this.databaseAdapter.query(query, logger)) as Array<ReadModelInterface>
      return keys
      /*l(`Obtainer following keys for query: ${keys}`)
      const results: ReadModelInterface[] = []
      await Promise.all(
        keys.map(async (k) => {
          const data = await this.databaseAdapter.hget<ReadModelEnvelope>(k)
          if (data?.value) {
            results.push(data.value)
          }
        })
      )
      l(`Got ${results} envelopes, returning`)
      return results*/
    }
  }

  public async store(readModel: ReadModelEnvelope, logger: Logger): Promise<void> {
    await this.databaseAdapter.storeReadModel(readModel, logger)
  }

  public async fetch(readModelName: string, readModelID: UUID, logger: Logger): Promise<ReadModelInterface | null> {
    //const key: string = this.readModelKey(readModelName, readModelID)
    //logger.debug('fetching key booster||', key)
    //const envelope = await this.databaseAdapter.hget<ReadModelEnvelope>(`booster||${key}`)
    //logger.debug('envelope fetched ' + JSON.stringify(envelope))
    return null
  }
}
