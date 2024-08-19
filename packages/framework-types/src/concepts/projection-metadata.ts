import { AnyClass } from '../typelevel'
import { ReadModelInterface } from './read-model'
import { EntityInterface } from './entity'
import { FilterFor } from '../searcher'

export type ReadModelJoinKeyFunction<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> = (
  entity: TEntity
) => FilterFor<TReadModel> | undefined

export interface ProjectionMetadata<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> {
  class: AnyClass
  methodName: string
  joinKey: keyof TEntity | ReadModelJoinKeyFunction<TEntity, TReadModel>
}

export type ProjectionResult<TReadModel> = TReadModel | ReadModelAction

export enum ReadModelAction {
  Delete,
  Nothing,
}
