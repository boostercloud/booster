/* eslint-disable @typescript-eslint/ban-types */
import { Class } from '@boostercloud/framework-types'
import { CallInformation, Tracer } from '../abstract-tracer'

/**
 * This decorator is used to trace the calls to the methods of a class,
 * it receives a tracer that will be used to trace the start, success and error of the method calls
 */
export const TracedWith =
  (configuredTracer: Tracer) =>
  <T>(target: Class<T>) => {
    const props = Object.getOwnPropertyNames(target.prototype) as Array<keyof T>
    const tracer = configuredTracer as Tracer<T>

    for (const prop of props) {
      const descriptor = getDescriptor(target, prop)
      if (!shouldTrace(prop, descriptor)) continue
      const originalMethod = descriptor.value
      descriptor.value = createTracingProxy(tracer, originalMethod, target, prop)
      Object.defineProperty(target.prototype, prop, descriptor)
    }
  }

/**
 * Returns the descriptor of the property in the target class
 */
const getDescriptor = <T>(target: Class<T>, prop: keyof T): PropertyDescriptor => {
  const descriptor = Object.getOwnPropertyDescriptor(target.prototype, prop)
  if (!descriptor) {
    throw new Error(`Error decorating class ${target.name} with TracedWith decorator: descriptor is undefined`)
  }
  return descriptor
}

/**
 * Returns true if the property is not the constructor and it is a function
 */
const shouldTrace = <T>(prop: keyof T, descriptor: PropertyDescriptor): boolean =>
  prop !== 'constructor' && typeof descriptor.value === 'function'

/**
 * Creates a proxy that will call the tracer methods before and after the method execution
 */
const createTracingProxy = <T>(
  tracer: Tracer<T>,
  originalMethod: Function,
  target: Class<T>,
  prop: keyof T
): Function =>
  new Proxy(originalMethod, {
    apply: (target: Class<T>, thisArg: T, argumentsList: unknown[]): unknown => {
      const callInfo = new CallInformation(target, thisArg, prop, argumentsList)
      tracer.onStart(callInfo)
      try {
        const result = originalMethod.apply(thisArg, argumentsList) as unknown
        if (result instanceof Promise) {
          return handlePromiseRejection(tracer, result, callInfo)
        }
        tracer.onSuccess(callInfo, result)
        return result
      } catch (error) {
        tracer.onError(callInfo, error)
        throw error
      }
    },
  })

/**
 * Attaches error handling to the promise so the catch is triggered when the promise is rejected
 */
const handlePromiseRejection = <T>(
  tracer: Tracer<T>,
  result: Promise<unknown>,
  callInfo: CallInformation<T>
): Promise<unknown> =>
  result.catch(async (error: unknown) => {
    tracer.onError(callInfo, error)
    throw error
  })
