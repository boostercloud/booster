import { EventEnvelope, Logger } from 'framework-types/dist'
import fetch from 'node-fetch'

export class EventRegistry {
  /**
   *
   */
  constructor(readonly url: string) {}

  public async store(event: EventEnvelope, logger: Logger): Promise<void> {
    const stateUrl = `${this.url}/v1.0/state/statestore`
    logger.debug('About to post', event)
    const response = await fetch(stateUrl, {
      method: 'POST',
      body: JSON.stringify(event),
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
}
