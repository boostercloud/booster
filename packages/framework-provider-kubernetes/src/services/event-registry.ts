/* eslint-disable @typescript-eslint/no-unused-vars */
import { EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { RedisAdapter } from './redis-adapter'

interface Query {
  keyQuery: string
  keyPredicate: (key: string) => boolean
  valuePredicate: (envelope: EventEnvelope) => boolean
  sortBy: (a: EventEnvelope, b: EventEnvelope) => number
}

export class EventRegistry {
  private readonly redis: RedisAdapter
  constructor(readonly url: string) {
    this.redis = RedisAdapter.build()
  }

  public async store(event: EventEnvelope, logger: Logger): Promise<void> {
    const stateUrl = `${this.url}/v1.0/state/statestore`
    logger.debug('About to post', event)
    const data = [{ key: this.eventKey(event), value: event }]
    const response = await fetch(stateUrl, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: {
        'Content-Type': 'application/json',
      },
    })
    if (!response.ok) {
      logger.error("Couldn't store event")
      const err = response.text()
      throw err
    }
  }

  public async query(query: Query, logger: Logger): Promise<Array<EventEnvelope>> {
    logger.debug('Getting redis keys')
    const redisKeys = await this.redis.keys(query.keyQuery, logger)
    logger.debug(`Got redis keys: ${JSON.stringify(redisKeys)}`)
    if (!redisKeys) {
      return []
    }
    logger.debug('Filtering keys')
    const keysToQuery = redisKeys.filter(query.keyPredicate)
    logger.debug(`Got filtered keys: ${JSON.stringify(keysToQuery)}`)
    logger.debug('Getting envelopes')
    const envelopes = (await Promise.all(keysToQuery.map((k) => this.redis.hget<EventEnvelope>(k))))
      .filter((envelope): envelope is EventEnvelope => envelope !== null)
      .filter(query.valuePredicate)
      .sort(query.sortBy)
    logger.debug(`Got ${envelopes.length} envelopes, returning`)
    return envelopes
  }

  public async queryLatest(query: Query, logger: Logger): Promise<EventEnvelope | null> {
    const result = await this.query(query, logger)
    if (result.length <= 0) {
      return null
    }
    return result[0]
  }

  private eventKey(event: EventEnvelope): string {
    const keyParts = [
      'ee', // event envelope marker
      event.entityTypeName, // 'Post' entity name
      event.entityID, // entityId
      event.kind, // 'event' | 'snapshot'
      event.typeName, // 'PostCreated' event name
      event.createdAt, // timespan
      UUID.generate(), // hash to make key unique
    ]
    return keyParts.join(RedisAdapter.keySeparator)
  }
}
