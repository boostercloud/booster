import { 
  BoosterConfig, 
  Logger, 
  ReadModelInterface,
  ReadModelEnvelope,
  UUID
} from '@boostercloud/framework-types'
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
    const readModelId = filters.id.eq
    if (readModelId) {
      const result = await fetch(`${this.url}/v1.0/state/statestore/${readModelId}`)
      try {
        const response = await result.json()
        if (response) {
          response.id = readModelId
        }
        return [response.value]
      } catch (err) {
        return []
      }
    } else {
      return []
    }
  }

  public async store(readModel: ReadModelEnvelope, logger: Logger): Promise<void> {
    await this.redis.set(this.readModelEnvelopeKey(readModel), readModel, logger)
  }

  public async fetch(readModelName: string, readModelID: UUID, logger: Logger): Promise<ReadModelInterface | null> {
    const key: string = this.readModelKey(readModelName, readModelID)
    logger.debug("fetching key booster||", key)
    const envelope = await this.redis.hget<ReadModelEnvelope>(`booster||${key}`)
    logger.debug("envelope fetched " + JSON.stringify(envelope))
    return envelope ? envelope.value : null
  }

  private readModelEnvelopeKey(readmodel: ReadModelEnvelope): string {
    return this.readModelKey(readmodel.typeName, readmodel.value.id)    
  }

  private readModelKey(typeName: string, id: UUID): string {
    const keyParts = [
      'rm', //Read Model mark
      typeName, //readModel type name
      id //readModel id
    ]
    return keyParts.join('_')
  }
}
