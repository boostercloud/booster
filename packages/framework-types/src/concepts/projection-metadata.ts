import { AnyClass } from '../typelevel'

export interface ProjectionMetadata<TEntity> {
  class: AnyClass
  methodName: string
  joinKey: keyof TEntity
}

export type ProjectionResult<TReadModel> = TReadModel | ReadModelAction

export enum ReadModelAction {
  Delete,
  Nothing,
}
