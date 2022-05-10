import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelInterface,
  UUID,
} from '@boostercloud/framework-types'

type PropType<TObj, TProp extends keyof TObj> = TObj[TProp]

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 * @param originEntity The entity that this method will react to
 */
export function Projects<TEntity extends EntityInterface, TJoinKey extends keyof TEntity>(
  originEntity: Class<TEntity>,
  joinKey: TJoinKey
): <TReadModel extends ReadModelInterface>(
  readModelClass: Class<TReadModel>,
  methodName: string,
  methodDescriptor: ProjectionMethod<TEntity, TReadModel, PropType<TEntity, TJoinKey>>
) => void {
  return (readModelClass, methodName) => {
    const projectionMetadata = {
      joinKey,
      class: readModelClass,
      methodName: methodName,
    } as ProjectionMetadata<EntityInterface>
    registerProjection(originEntity.name, projectionMetadata)
  }
}

function registerProjection(originName: string, projectionMetadata: ProjectionMetadata<EntityInterface>): void {
  Booster.configureCurrentEnv((config): void => {
    const entityProjections = config.projections[originName] || []
    if (entityProjections.indexOf(projectionMetadata) < 0) {
      // Skip duplicate registrations
      entityProjections.push(projectionMetadata)
      config.projections[originName] = entityProjections
    }
  })
}

type ProjectionMethod<TEntity, TReadModel, TPropType> = TPropType extends Array<UUID>
  ? TypedPropertyDescriptor<(_: TEntity, readModelID: UUID, readModel?: TReadModel) => ProjectionResult<TReadModel>>
  : TypedPropertyDescriptor<(_: TEntity, readModel?: TReadModel) => ProjectionResult<TReadModel>>
