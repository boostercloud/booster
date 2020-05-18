/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'

export class RawEventsParser {
  public static async streamEvents(
    config: BoosterConfig,
    rawEvents: any,
    callbackFn: (eventEnvelope: EventEnvelope, config: BoosterConfig) => Promise<void>
  ): Promise<void> {
    for (const event of config.provider.events.rawToEnvelopes(rawEvents)) {
      await callbackFn(event, config)
    }
  }
}
