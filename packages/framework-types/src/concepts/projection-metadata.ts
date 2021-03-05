import { Class } from '..'

export interface ProjectionMetadata {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  class: Class<any>
  methodName: string
  joinKey: string
  hasBeenProjected?: boolean
}

export type ProjectionResult<TReadModel> = TReadModel | ReadModelAction

export enum ReadModelAction {
  Delete,
  Nothing,
}