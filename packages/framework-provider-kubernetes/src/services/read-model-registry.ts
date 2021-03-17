import { BoosterConfig, Logger, ReadModelInterface } from '@boostercloud/framework-types'
import fetch from 'node-fetch'

// TODO: Implement querying with filters properly
interface Filters {
  id: {
    eq: string
  }
}

export class ReadModelRegistry {
  constructor(readonly url: string) {}

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
}
