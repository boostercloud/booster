import { BoosterConfig, TraceActionTypes, TraceInfo, TraceTypes, UUID } from '@boostercloud/framework-types'
import { isTraceEnabled, notifyTrace } from '../trace-notifier'
import { Booster } from '../../booster'

export function Trace(actionType: string = TraceActionTypes.CUSTOM, description?: string) {
  return (
    target: unknown,
    member: string,
    descriptor: TypedPropertyDescriptor<(...params: any[]) => Promise<any>>
  ): PropertyDescriptor => {
    const originalMethod = descriptor.value!
    descriptor.value = async function (...args: Array<unknown>) {
      const config = Booster.config
      const tracerConfigured = isTraceEnabled(actionType, config)
      if (!tracerConfigured) {
        return await originalMethod.apply(this, args)
      }
      const parameters = buildParameters(target, member, args, description, descriptor, config)
      const startTime = new Date().getTime()
      await notifyTrace(TraceTypes.START, actionType, parameters, config)
      try {
        return await originalMethod.apply(this, args)
      } finally {
        parameters.elapsedInvocationMillis = new Date().getTime() - startTime
        await notifyTrace(TraceTypes.END, actionType, parameters, config)
      }
    }
    return descriptor
  }
}

function buildParameters(
  target: unknown,
  member: string,
  args: unknown[],
  description: string | undefined,
  descriptor: PropertyDescriptor,
  config: BoosterConfig
) {
  let internal = undefined
  if (config && config.traceConfiguration.includeInternal) {
    internal = {
      target: target,
      descriptor: descriptor,
    }
  }
  const parameters: TraceInfo = {
    className: getClassName(target),
    methodName: member,
    args: args,
    description: description,
    traceId: UUID.generate(),
    internal: internal,
  }
  return parameters
}

// Get class name for instances and static methods
function getClassName(target: unknown) {
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  return target?.prototype?.constructor?.name ?? target?.constructor?.name ?? ''
}
