import { Class } from '..'

export interface ReducerMetadata {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  class: Class<any>
  methodName: string
}
