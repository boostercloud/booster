import { TraceParameters, BoosterConfig, TraceActionTypes, TraceTypes } from '@boostercloud/framework-types'

export class CustomTracer {
  constructor() {}

  onStart(config: BoosterConfig, actionType: TraceActionTypes, traceParameters: TraceParameters): void {
    console.log(`${TraceTypes[TraceTypes.START]}_${TraceActionTypes[actionType]}`, traceParameters)
  }

  onEnd(config: BoosterConfig, actionType: TraceActionTypes, traceParameters: TraceParameters): void {
    console.log(
      `${TraceTypes[TraceTypes.END]}_${TraceActionTypes[actionType]} (${traceParameters.elapsedInvocationMillis} ms)\n`,
      traceParameters
    )
  }
}
