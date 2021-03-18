/* eslint-disable @typescript-eslint/no-unused-vars */
import { EventEnvelope, Logger } from 'framework-types/dist'
import { v4 as uuidv4 } from 'uuid';
import fetch from 'node-fetch'

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

  public async query(_query: object, _logger: Logger): Promise<Array<EventEnvelope>> {
    // TODO: Implement accordingly to Dapr
    throw new Error('EventRegistry#query: Not implemented yet')
  }

  public async queryLatest(_query: object): Promise<EventEnvelope> {
    // TODO: Implement accordingly to Dapr
    throw new Error('EventRegistry#queryLatest: Not implemented yet')
  }

  private eventKey(event: EventEnvelope): string {
    //ee_Post_95ddb544-4a60-439f-a0e4-c57e806f2f6e_event_PostCreated_2021-03-17T16:49:29.143Z_j9qn8kd8
    const keyParts = [
      'ee',                 // event envelope marker
      event.entityTypeName, // 'Post' entity name
      event.entityID,       // entityId
      event.kind,           // 'event' | 'snapshot'
      event.typeName,       // 'PostCreated' event name
      event.createdAt,      // timespan
      uuidv4()              // hash to make key unique
    ]
    return keyParts.join('_')
  }
}
