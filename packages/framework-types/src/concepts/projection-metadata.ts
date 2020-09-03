import { Class } from '..'

export interface ProjectionMetadata {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  class: Class<any>
  methodName: string
  joinKey: string
}

export type ProjectionResult<TResult> = TResult | typeof deleteReadModel

export const deleteReadModel = { _tag: 'ProjectionResult', value: 'deleteReadModel' }
