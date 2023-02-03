/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  EventEnvelope,
  EntitySnapshotEnvelope,
  UUID,
  NonPersistedEventEnvelope,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
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

  public async storeEvent(config: BoosterConfig, event: NonPersistedEventEnvelope): Promise<void> {
    const logger = getLogger(config, 'event-registry#store')
    const stateUrl = `${this.url}/v1.0/state/statestore`
    logger.debug('About to post', event)
    const persistableEvent: EventEnvelope = {
      ...event,
      persistedAt: new Date().toISOString(),
    }
    const data = [{ key: this.eventKey(persistableEvent), value: persistableEvent }]
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

  public async storeSnapshot(config: BoosterConfig, snapshot: EntitySnapshotEnvelope): Promise<void> {
    const logger = getLogger(config, 'event-registry#storeSnapshot')
    const stateUrl = `${this.url}/v1.0/state/statestore`
    logger.debug('About to post', snapshot)
    const data = [{ key: this.snapshotKey(snapshot), value: snapshot }]
    const response = await fetch(stateUrl, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: {
        'Content-Type': 'application/json',
      },
    })
    if (!response.ok) {
      logger.error("Couldn't store snapshot")
      const err = response.text()
      throw err
    }
  }

  public async query(config: BoosterConfig, query: Query): Promise<Array<EventEnvelope | EntitySnapshotEnvelope>> {
    const logger = getLogger(config, 'event-registry#query')
    logger.debug('Getting redis keys')
    const redisKeys = await this.redis.keys(config, query.keyQuery)
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

  public async queryLatest(
    config: BoosterConfig,
    query: Query
  ): Promise<EventEnvelope | EntitySnapshotEnvelope | undefined> {
    const result = await this.query(config, query)
    if (result.length <= 0) {
      return undefined
    }
    return result[result.length - 1]
  }

  private eventKey(event: EventEnvelope): string {
    const keyParts = [
      'ee', // event envelope marker
      event.entityTypeName, // 'Post' entity name
      event.entityID, // entityId
      event.kind, // 'event' | 'snapshot'
      event.typeName, // 'PostCreated' event name
      event.persistedAt, // timespan
      UUID.generate(), // hash to make key unique
    ]
    return keyParts.join(RedisAdapter.keySeparator)
  }

  private snapshotKey(snapshot: EntitySnapshotEnvelope): string {
    const keyParts = [
      'ee', // event envelope marker
      snapshot.entityTypeName, // 'Post' entity name
      snapshot.entityID, // entityId
      snapshot.kind, // 'event' | 'snapshot'
      snapshot.version, // snapshot version
      snapshot.snapshottedEventPersistedAt, // timespan
      UUID.generate(), // hash to make key unique
    ]
    return keyParts.join(RedisAdapter.keySeparator)
  }
}
