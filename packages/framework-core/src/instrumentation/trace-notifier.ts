import { TraceActionTypes, TraceParameters, TraceTypes } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export async function notifyTrace(
  type: TraceTypes,
  actionType: TraceActionTypes,
  parameters: TraceParameters
): Promise<void> {
  const config = Booster.config
  const handler = type === TraceTypes.START ? config.traceConfiguration.onStart : config.traceConfiguration.onEnd
  return handler(config, actionType, parameters)
}

export function isTracerConfigured(actionType: TraceActionTypes): boolean {
  const config = Booster.config
  if (!config) {
    return false
  }
  const invocations = config.traceConfiguration
  if (!invocations) {
    return false
  }
  const enableNotificationsInvocations = invocations.enableTraceNotification
  if (!enableNotificationsInvocations) {
    return false
  }
  if (Array.isArray(enableNotificationsInvocations) && !enableNotificationsInvocations.includes(actionType)) {
    return false
  }
  return true
}
