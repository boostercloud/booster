/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'
import { Providers } from '../providers'

export class RawEventsParser {
  public static async streamEvents(
    config: BoosterConfig,
    rawEvents: any,
    callbackFn: (eventEnvelope: EventEnvelope) => Promise<void>
  ): Promise<void> {
    const provider = Providers.getLibrary(config)
    for (const event of provider.rawEventsToEnvelopes(rawEvents)) {
      await callbackFn(event)
    }
  }
}
