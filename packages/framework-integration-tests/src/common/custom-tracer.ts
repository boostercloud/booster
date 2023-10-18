import { TraceInfo, BoosterConfig, TraceTypes } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export class CustomTracer {
  static async onStart(config: BoosterConfig, actionType: string, traceInfo: TraceInfo): Promise<void> {
    const logger = getLogger(config, 'CustomTracer#onStart')
    logger.debug(`${TraceTypes[TraceTypes.START]}: ${actionType}`, traceInfo)
  }

  static async onEnd(config: BoosterConfig, actionType: string, traceInfo: TraceInfo): Promise<void> {
    const logger = getLogger(config, 'CustomTracer#onEnd')
    logger.debug(`${TraceTypes[TraceTypes.END]}: ${actionType} (${traceInfo.elapsedInvocationMillis} ms)\n`, traceInfo)
  }
}
