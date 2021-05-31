import { Class } from '@boostercloud/framework-types'

export function createInstance<T>(instanceClass: Class<T>, rawObject: Record<string, any>): T {
  const instance = new instanceClass()
  Object.assign(instance, rawObject)
  return instance
}
