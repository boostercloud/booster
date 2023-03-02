/* eslint-disable @typescript-eslint/no-unused-vars */
import { AnyClass, Class, HasLogger } from '@boostercloud/framework-types'
import Brand from './brand'

export interface ComponentContainer<T> {
  /**
   * Gets a component from the container
   */
  get: <C extends T>(component: abstract new () => C) => C
}

export interface ComponentOptions<TError> {
  throws: Class<TError>
}

export interface HasCatchMethod<TError> {
  catch: (error: unknown) => Promise<TError>
}

/**
 * Decorator to be used to define components, it has the constraint that the class must have a logger
 * property, and it will log all the calls to the methods of the class if the logger is in debug mode.
 *
 * Also, it will ensure that the class has a catch method to handle errors.
 */
export const Component =
  <TError extends Error = Error>(_?: ComponentOptions<TError>) =>
  <T extends HasLogger & HasCatchMethod<TError>>(target: Class<T>) => {
    // Get all the property names of the target class
    const props = Object.getOwnPropertyNames(target.prototype)
    // Loop through each property name
    for (const prop of props) {
      // Get the property descriptor
      const descriptor = Object.getOwnPropertyDescriptor(target.prototype, prop)
      // Throw if descriptor is undefined, this should never happen
      if (!descriptor) {
        throw new Error(
          'An error occurred while trying to get the property descriptor for ' +
            prop +
            'on prototype' +
            target.prototype +
            ', it was undefined'
        )
      }
      // Check if the property is a method (and not the constructor)
      if (prop !== 'constructor' && typeof descriptor.value === 'function') {
        // Wrap the original method with a proxy object that logs it
        const origMethod = descriptor.value
        descriptor.value = new Proxy(origMethod, {
          apply: function (target: Class<T>, thisArg: T, argumentsList: unknown[]): unknown {
            const logger = thisArg.logger
            logger.debug(
              `\n${Brand.mellancholize('[DEBUG]')} ${thisArg.constructor.name}#${prop}(${argumentsList
                .map((x) => JSON.stringify(x))
                .join(', ')})`
            )
            const errorMessage = (error: unknown) => `${Brand.dangerize('[ERROR]')} at ${target.name}#${prop}: ${error}`
            try {
              const result = target.apply(thisArg, argumentsList) as unknown
              if (result instanceof Promise) {
                return result.catch((error: unknown) => {
                  logger.debug(errorMessage(error))
                  throw thisArg.catch(error)
                })
              }
              return result
            } catch (error) {
              logger.debug(errorMessage(error))
              throw error
            }
          },
        })
        // Redefine the property with the new descriptor
        Object.defineProperty(target.prototype, prop, descriptor)
      }
    }
  }

/**
 * Decorator to be used to define logger components. It is separate from the Component decorator because
 * it is used for defining loggers, and loggers are used by the Component decorator, so you would have
 * a circular dependency if you used the Component decorator for loggers.
 */
export function LoggerComponent() {
  return <T extends AnyClass>(target: T): T => {
    return target
  }
}
