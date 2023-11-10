import { EntityInterface, ReadModelInterface, ReadModelJoinKeyFunction } from '@boostercloud/framework-types'

export function isJoinKeyByEntity<TEntity extends EntityInterface, TReadModel extends ReadModelInterface>(
  projectionMetadataJoinKey: keyof TEntity | ReadModelJoinKeyFunction<TEntity, TReadModel>
): projectionMetadataJoinKey is keyof TEntity {
  return typeof projectionMetadataJoinKey === 'string'
}
