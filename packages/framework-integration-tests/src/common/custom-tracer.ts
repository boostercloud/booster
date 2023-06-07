import { TraceInfo, BoosterConfig, TraceTypes } from '@boostercloud/framework-types'

export class CustomTracer {
  static async onStart(config: BoosterConfig, actionType: string, traceInfo: TraceInfo): Promise<void> {
    console.log(`${TraceTypes[TraceTypes.START]}: ${actionType}`, traceInfo)
  }

  static async onEnd(config: BoosterConfig, actionType: string, traceInfo: TraceInfo): Promise<void> {
    console.log(
      `${TraceTypes[TraceTypes.END]}: ${actionType} (${traceInfo.elapsedInvocationMillis} ms)\n`,
      traceInfo
    )
  }
}
