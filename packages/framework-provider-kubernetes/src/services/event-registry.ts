/* eslint-disable @typescript-eslint/no-unused-vars */
import { EventEnvelope, Logger } from '@boostercloud/framework-types'
import { Query } from '../types/query'
import { DatabaseAdapter } from './database-adapter'

export class EventRegistry {
  private readonly eventStore = new DatabaseAdapter()

  public async store(event: EventEnvelope, logger: Logger): Promise<void> {
    await this.eventStore.storeEvent(event, logger)
  }

  public async query(query: Query, logger: Logger): Promise<Array<EventEnvelope>> {
    logger.debug('Getting redis keys')
    const redisKeys = (await this.eventStore.query(query, logger)) as Array<EventEnvelope>
    logger.debug(`Got redis keys: ${JSON.stringify(redisKeys)}`)
    if (!redisKeys) {
      return []
    }
    return redisKeys
  }
  /*logger.debug('Filtering keys')
    const keysToQuery = redisKeys.filter(query.keyPredicate)
    logger.debug(`Got filtered keys: ${JSON.stringify(keysToQuery)}`)
    logger.debug('Getting envelopes')
    const envelopes = (await Promise.all(keysToQuery.map((k) => this.eventStore.hget<EventEnvelope>(k))))
      .filter((envelope): envelope is EventEnvelope => envelope !== null)
      .filter(query.valuePredicate)
      .sort(query.sortBy)
    logger.debug(`Got ${envelopes.length} envelopes, returning`)*/

  public async queryLatest(query: Query, logger: Logger): Promise<EventEnvelope | null> {
    const result = await this.query(query, logger)
    if (result.length <= 0) {
      return null
    }
    return result[result.length - 1]
  }
}
