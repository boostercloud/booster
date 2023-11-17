import { Trace } from './instrumentation'
import { BoosterConfig, TraceActionTypes } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { RawEventsParser } from './services/raw-events-parser'

export class BoosterEventStreamProducer {
  @Trace(TraceActionTypes.PRODUCE_STREAM_EVENTS)
  public static async produce(request: unknown, config: BoosterConfig): Promise<void> {
    const logger = getLogger(config, 'BoosterEventStreamProducer#produce')
    logger.debug('Produce event workflow started for request:', require('util').inspect(request, false, null, false))
    try {
      const eventEnvelopes = config.provider.events.rawToEnvelopes(request)
      await RawEventsParser.streamPerEntityEvents(config, eventEnvelopes, config.provider.events.produce)
    } catch (e) {
      logger.error('Unhandled error while producing events: ', e)
    }
  }
}
