import { Class } from './typelevel'

export function createInstance<T>(instanceClass: Class<T>, rawObject: Record<string, any>): T {
  const instance = new instanceClass()
  Object.assign(instance, rawObject)
  return instance
}

export function createInstances<T>(instanceClass: Class<T>, rawObjects: Array<Record<string, any>>): T[] {
  return rawObjects.map((rawObject) => {
    const instance = new instanceClass()
    Object.assign(instance, rawObject)
    return instance
  })
}
