/* eslint-disable @typescript-eslint/no-explicit-any */
import { Class, ProjectionFor, ReadModelInterface } from '@boostercloud/framework-types'

/**
 * Creates an instance of the given class from the given raw object.
 *
 * @param instanceClass The class of the instance to create
 * @param rawObject The raw object to use as source
 *
 * @returns An instance of the given class
 *
 * @example
 * ```typescript
 * import { createInstance } from '@boostercloud/framework-common-helpers'
 * import { User } from './entities/user'
 *
 * const rawUser = {
 *   id: '123',
 *   name: 'John Doe',
 * }
 *
 * const user = createInstance(User, rawUser)
 *
 * console.log(user.id) // Prints '123'
 * console.log(user.name) // Prints 'John Doe'
 * ```
 */
export function createInstance<T>(instanceClass: Class<T>, rawObject: Record<string, any>): T {
  const instance = new instanceClass()
  return Object.assign(instance as any, rawObject)
}

/**
 * Creates an array of instances of the given class from the given array of raw objects.
 *
 * @param instanceClass The class of the instances to create
 * @param rawObjects The array of raw objects to use as source
 *
 * @returns An array of instances of the given class
 *
 * @see {@link createInstance}
 */
export function createInstances<T>(instanceClass: Class<T>, rawObjects: Array<Record<string, any>>): T[] {
  return rawObjects.map((rawObject) => createInstance(instanceClass, rawObject))
}

/**
 * Creates an instance of the read model class with the calculated properties included
 * @param instanceClass The read model class
 * @param raw The raw read model data
 * @param propertiesToInclude The properties to include in the response
 * @private
 */
export async function createInstanceWithCalculatedProperties<T extends ReadModelInterface>(
  instanceClass: Class<T>,
  raw: Partial<T>,
  propertiesToInclude: ProjectionFor<T>
): Promise<T> {
  const instance = new instanceClass()
  Object.assign(instance, raw)
  const result: T = {} as T

  const propertiesMap = buildPropertiesMap(propertiesToInclude)

  await processProperties(instance, result, propertiesMap)

  return result
}

/**
 * Builds a map of properties to include in the response
 * @param properties The properties to include in the response
 * @private
 */
function buildPropertiesMap<T>(properties: ProjectionFor<T>): any {
  const map: any = {}
  properties.forEach((property) => {
    const parts = property.split('.')
    let current = map
    parts.forEach((part) => {
      const isArray = part.endsWith('[]')
      const key = isArray ? part.slice(0, -2) : part
      if (!Object.prototype.hasOwnProperty.call(current, key)) {
        current[key] = isArray ? { __isArray: true, __children: {} } : {}
      }
      current = isArray ? current[key].__children : current[key]
    })
  })
  return map
}

/**
 * Processes the properties of the source object and adds them to the result object
 * @param source The source object
 * @param result The result object
 * @param propertiesMap The map of properties to include in the response
 * @private
 */
async function processProperties(source: any, result: any, propertiesMap: any): Promise<void> {
  for (const key of Object.keys(propertiesMap)) {
    if (key === '__isArray' || key === '__children') continue

    if (source[key] !== undefined) {
      if (propertiesMap[key].__isArray) {
        result[key] = []
        for (const item of source[key]) {
          const newItem: any = {}
          await processProperties(item, newItem, propertiesMap[key].__children)
          if (Object.keys(newItem).length > 0) {
            result[key].push(newItem)
          }
        }
      } else if (typeof propertiesMap[key] === 'object' && Object.keys(propertiesMap[key]).length > 0) {
        const value = source[key]
        const resolvedValue = isPromise(value) ? await value : value
        if (resolvedValue === undefined || resolvedValue === null) {
          result[key] = null
        } else {
          result[key] = {}
          await processProperties(resolvedValue, result[key], propertiesMap[key])
          if (Object.keys(result[key]).length === 0) {
            result[key] = null
          }
        }
      } else {
        const value = source[key]
        result[key] = isPromise(value) ? await value : value
      }
    } else {
      // Handle the case when the source property is undefined
      if (typeof propertiesMap[key] === 'object' && Object.keys(propertiesMap[key]).length > 0) {
        result[key] = null
      }
    }
  }
}

function isPromise(obj: any): obj is Promise<any> {
  return !!obj && (typeof obj === 'object' || typeof obj === 'function') && typeof obj.then === 'function'
}
