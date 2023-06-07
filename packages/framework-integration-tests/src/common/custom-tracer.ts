import { TraceInfo, BoosterConfig, TraceActionTypes, TraceTypes } from '@boostercloud/framework-types'

export class CustomTracer {
  static async onStart(config: BoosterConfig, actionType: TraceActionTypes, traceInfo: TraceInfo): Promise<void> {
    console.log(`${TraceTypes[TraceTypes.START]}_${TraceActionTypes[actionType]}`, traceInfo)
  }

  static async onEnd(config: BoosterConfig, actionType: TraceActionTypes, traceInfo: TraceInfo): Promise<void> {
    console.log(
      `${TraceTypes[TraceTypes.END]}_${TraceActionTypes[actionType]} (${traceInfo.elapsedInvocationMillis} ms)\n`,
      traceInfo
    )
  }
}
