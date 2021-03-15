import { BoosterConfig, Logger, FilterOld, ReadModelInterface } from '@boostercloud/framework-types'
import fetch from 'node-fetch'

// TODO: Implement querying with filters properly
interface Filters {
  id: {
    values: Array<string>
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
    const readModelId = filters.id.values[0]
    if (readModelId) {
      const result = await fetch(`${this.url}/v1.0/state/statestore/${readModelId}`)
      try {
        const response = await result.json()
        if (response) {
          response.id = readModelId
        }
        return [response]
      } catch (err) {
        return []
      }
    } else {
      return []
    }
  }
}
