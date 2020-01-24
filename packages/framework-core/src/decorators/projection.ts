import { Booster } from '../booster'
import { Class, EntityInterface, ProjectionMetadata } from '@boostercloud/framework-types'

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 * @param originEntity The entity that this method will react to
 */
export function Projection<TEntity extends EntityInterface>(
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

function registerProjection(originName: string, reducerMetadata: ProjectionMetadata): void {
  Booster.configure((config): void => {
    const entityProjections = config.projections[originName] || []
    if (entityProjections.indexOf(reducerMetadata) < 0) {
      // Skip duplicate registrations
      entityProjections.push(reducerMetadata)
      config.projections[originName] = entityProjections
    }
  })
}

type ProjectionMethod<TEntity, TReadModel> = TypedPropertyDescriptor<(_: TEntity, readModel?: TReadModel) => TReadModel>
