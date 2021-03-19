import { 
  BoosterConfig, 
  Logger, 
  ReadModelInterface,
  ReadModelEnvelope
} from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { RedisAdapter } from './redis-adapter';

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
    this.redis.set(this.readModelKey(readModel), readModel, logger)
  }

  private readModelKey(readmodel: ReadModelEnvelope): string {
    const keyParts = [
      'rm', //Read Model mark
      readmodel.typeName, //readModel type name
      readmodel.value.id //readModel id
    ]
    return keyParts.join('_')
  }
}
