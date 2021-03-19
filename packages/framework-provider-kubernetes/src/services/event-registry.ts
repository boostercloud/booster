/* eslint-disable @typescript-eslint/no-unused-vars */
import { EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { RedisAdapter } from './redis-adapter'
//import { RedisAdapter } from './redis-adapter'

export class EventRegistry {
  constructor(readonly url: string) {}

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

  public async query(query: string, logger: Logger): Promise<Array<EventEnvelope>> {
    const redisKeys = await RedisAdapter.build().keys(query, logger)
    if (!redisKeys) {
      return []
    }
    console.log(redisKeys)
    throw new Error('EventRegistry#query: not implemented to get snapshots from redis')
  }

  public async queryLatest(query: string, logger: Logger): Promise<EventEnvelope | null> {
    const redisKeys = await RedisAdapter.build().keys(query, logger)
    if (!redisKeys) {
      return null
    }
    console.log(redisKeys)
    throw new Error('EventRegistry#queryLatest: not implemented get snapshots from redis')
  }

  private eventKey(event: EventEnvelope): string {
    const keyParts = [
      'ee', // event envelope marker
      event.entityTypeName, // 'Post' entity name
      event.entityID, // entityId
      event.kind, // 'event' | 'snapshot'
      event.typeName, // 'PostCreated' event name
      event.createdAt, // timespan
      new UUID(), // hash to make key unique
    ]
    return keyParts.join('_')
  }
}
