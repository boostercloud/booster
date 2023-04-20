import { Class } from '@boostercloud/framework-types'

/**
 * This class is used to pass information about the method call to the tracer
 * @param classValue The class where the method is defined
 * @param thisValue The instance of the class where the method is defined
 * @param methodName The name of the method
 * @param methodArgs The arguments passed to the method
 */
export class CallInformation<T> {
  constructor(
    readonly classValue: Class<T>,
    readonly thisValue: T,
    readonly methodName: keyof T,
    readonly methodArgs: ReadonlyArray<unknown>
  ) {}
}

/**
 * This class is used to trace the calls to the methods of a class
 * @param onStart This method is called before the method is called
 * @param onSuccess This method is called after the method is called and it has not thrown any error
 * @param onError This method is called after the method is called and it has thrown an error
 */
export abstract class Tracer<T = unknown> {
  abstract onStart(callInformation: CallInformation<T>): void
  abstract onSuccess(callInformation: CallInformation<T>, result: unknown): void
  abstract onError(callInformation: CallInformation<T>, error: unknown): void
}
