import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelInterface,
  ReadModelJoinKeyFunction,
  UUID,
} from '@boostercloud/framework-types'

type PropertyType<TObj, TProp extends keyof TObj> = TObj[TProp]
type JoinKeyType<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> =
  | keyof TEntity
  | ReadModelJoinKeyFunction<TEntity, TReadModel>

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 * @param originEntity The entity that this method will react to
 * @param joinKey
 */
export function Projects<TEntity extends EntityInterface, TReadModel extends ReadModelInterface>(
  originEntity: Class<TEntity>,
  joinKey: JoinKeyType<TEntity, TReadModel>
): <TReceivedReadModel extends ReadModelInterface>(
  readModelClass: Class<TReceivedReadModel>,
  methodName: string,
  methodDescriptor: ProjectionMethod<TEntity, TReceivedReadModel, JoinKeyType<TEntity, TReceivedReadModel>>
) => void {
  return (readModelClass, methodName) => {
    const projectionMetadata = {
      joinKey: joinKey,
      class: readModelClass,
      methodName: methodName,
    } as ProjectionMetadata<EntityInterface, ReadModelInterface>
    registerProjection(originEntity.name, projectionMetadata)
  }
}

function registerProjection(
  originName: string,
  projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>
): void {
  Booster.configureCurrentEnv((config): void => {
    const entityProjections = config.projections[originName] || []
    if (entityProjections.indexOf(projectionMetadata) < 0) {
      // Skip duplicate registrations
      entityProjections.push(projectionMetadata)
      config.projections[originName] = entityProjections
    }
  })
}

type ProjectionMethod<
  TEntity extends EntityInterface,
  TReadModel extends ReadModelInterface,
  TJoinKeyType extends JoinKeyType<TEntity, TReadModel>
> = TJoinKeyType extends ReadModelJoinKeyFunction<TEntity, TReadModel>
  ? ProjectionMethodWithEntityReadModelIdAndReadModel<TEntity, TReadModel>
  : TJoinKeyType extends keyof TEntity
  ? PropertyType<TEntity, TJoinKeyType> extends Array<UUID>
    ? ProjectionMethodWithEntityReadModelIdAndReadModel<TEntity, TReadModel>
    : ProjectionMethodWithEntityAndReadModel<TEntity, TReadModel>
  : never

type ProjectionMethodWithEntityAndReadModel<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> =
  TypedPropertyDescriptor<(_: TEntity, readModel?: TReadModel) => ProjectionResult<TReadModel>>

type ProjectionMethodWithEntityReadModelIdAndReadModel<
  TEntity extends EntityInterface,
  TReadModel extends ReadModelInterface
> = TypedPropertyDescriptor<(_: TEntity, readModelID: UUID, readModel?: TReadModel) => ProjectionResult<TReadModel>>
