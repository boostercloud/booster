/**
 * Interface to get information from a `TReflected`
 * class.
 *
 * Usage:
 *
 * ```typescript
 * function printName<T>(cls : Class<T>){
 *   console.log(cls.name)
 * }
 *
 * printName(Person)  // Prints "Person"
 * ```
 */
export interface Class<TReflected> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  new (...args: any[]): TReflected
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface,@typescript-eslint/no-explicit-any
export interface AnyClass extends Class<any> {}

export interface Instance {
  constructor: {
    name: string
  }
}

export function toClassTitle(instance: Instance): string {
  return instance.constructor.name
    .replace(/([A-Z])([a-z])/g, ' $1$2')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
}
