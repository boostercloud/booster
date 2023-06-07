import { BoosterConfig, TraceInfo, TraceTypes } from '@boostercloud/framework-types'

export async function notifyTrace(
  type: TraceTypes,
  actionType: string,
  parameters: TraceInfo,
  config: BoosterConfig
): Promise<void> {
  const handler = type === TraceTypes.START ? config.traceConfiguration.onStart : config.traceConfiguration.onEnd
  return handler.call(handler, config, actionType, parameters)
}

export function isTraceEnabled(actionType: string, config: BoosterConfig): boolean {
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
