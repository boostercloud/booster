import { Class } from '@boostercloud/framework-types'
import { plainToClass } from 'class-transformer'

export function createInstance<T>(instanceClass: Class<T>, rawObject: Record<string, any>): T {
  return plainToClass(instanceClass, rawObject)
}

export function createInstances<T>(instanceClass: Class<T>, rawObjects: Array<Record<string, any>>): T[] {
  return rawObjects.map((rawObject) => createInstance(instanceClass, rawObject))
}
