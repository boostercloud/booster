import { Class } from '@boostercloud/framework-types'
import { plainToClass } from 'class-transformer'

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
export function createInstance<T, V>(instanceClass: Class<T>, rawObject: V): T {
  return plainToClass(instanceClass, rawObject)
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
export function createInstances<T, V>(instanceClass: Class<T>, rawObjects: Array<V>): T[] {
  return rawObjects.map((rawObject) => createInstance(instanceClass, rawObject))
}
