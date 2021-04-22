import { BoosterConfig, Logger, ReadModelInterface, ReadModelEnvelope, UUID } from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { RedisAdapter } from './redis-adapter'

// TODO: Implement querying with filters properly
interface Filters {
  id: {
    eq: string
  }
}

export class ReadModelRegistry {
  private readonly redis: RedisAdapter

  constructor(readonly url: string) {
    this.redis = RedisAdapter.build()
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
      const url = `${this.url}/v1.0/state/statestore/${this.readModelKey(readModelName, readModelId)}`
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
      const keys = await this.redis.keys(['rm',readModelName,'*'].join(RedisAdapter.keySeparator), logger)
      l(`Obtainer following keys for query: ${keys}`)
      const results: ReadModelInterface[] = []
      await Promise.all(
        keys.map(async (k) => {
          const data = await this.redis.hget<ReadModelEnvelope>(k)
          if (data?.value) {
            results.push(data.value)
          }
        })
      )
      l(`Got ${results} envelopes, returning`)
      return results
    }
  }

  public async store(readModel: ReadModelEnvelope, logger: Logger): Promise<void> {
    await this.redis.set(this.readModelEnvelopeKey(readModel), readModel, logger)
  }

  public async fetch(readModelName: string, readModelID: UUID, logger: Logger): Promise<ReadModelInterface | null> {
    const key: string = this.readModelKey(readModelName, readModelID)
    logger.debug('fetching key booster||', key)
    const envelope = await this.redis.hget<ReadModelEnvelope>(`booster||${key}`)
    logger.debug('envelope fetched ' + JSON.stringify(envelope))
    return envelope ? envelope.value : null
  }

  private readModelEnvelopeKey(readmodel: ReadModelEnvelope): string {
    return this.readModelKey(readmodel.typeName, readmodel.value.id)
  }

  private readModelKey(typeName: string, id: UUID): string {
    const keyParts = [
      'rm', //Read Model mark
      typeName, //readModel type name
      id, //readModel id
    ]
    return keyParts.join(RedisAdapter.keySeparator)
  }
}
