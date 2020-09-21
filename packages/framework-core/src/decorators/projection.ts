import { Booster } from '../booster'
import { Class, EntityInterface, ProjectionMetadata, ProjectionResult } from '@boostercloud/framework-types'

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 * @param originEntity The entity that this method will react to
 */
export function Projects<TEntity extends EntityInterface>(
  originEntity: Class<TEntity>,
  joinKey: string
): <TReadModel>(
  readModelClass: Class<TReadModel>,
  methodName: string,
  methodDescriptor: ProjectionMethod<TEntity, TReadModel>
) => void {
  return (readModelClass, methodName) => {
    registerProjection(originEntity.name, {
      joinKey: joinKey,
      class: readModelClass,
      methodName: methodName,
    })
  }
}

function registerProjection(originName: string, projectionMetadata: ProjectionMetadata): void {
  Booster.configureCurrentEnv((config): void => {
    const entityProjections = config.projections[originName] || []
    if (entityProjections.indexOf(projectionMetadata) < 0) {
      // Skip duplicate registrations
      entityProjections.push(projectionMetadata)
      config.projections[originName] = entityProjections
    }
  })
}

type ProjectionMethod<TEntity, TReadModel> = TypedPropertyDescriptor<
  (_: TEntity, readModel?: TReadModel) => ProjectionResult<TReadModel>
>
