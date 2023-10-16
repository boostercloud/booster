import {
  BoosterConfig,
  EntityInterface,
  EntityMetadata,
  FilterFor,
  ProjectionMetadata,
  ReadModelInterface,
  ReadModelJoinKeyFunction,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { isJoinKeyByEntity } from './read-model-project-utils'

/**
 * Build Filters for ReadModels queries based on the projection metadata
 */
export class ProjectFilterFor {
  constructor(
    readonly config: BoosterConfig,
    readonly entity: EntityInterface,
    readonly projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    readonly entityMetadata: EntityMetadata
  ) {}

  public filterForProjection(): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ProjectFilterFor#filterForProjection')
    const projectionMetadataJoinKey = this.projectionMetadata.joinKey
    logger.debug(`Looking for ReadModels using Filter ${projectionMetadataJoinKey}`)
    if (isJoinKeyByEntity(projectionMetadataJoinKey)) {
      return this.filterForEntityProjection()
    }

    return this.filterForReadModelProjection()
  }

  filterForEntityProjection<TEntity extends EntityInterface>(): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ProjectFilterFor#filterForEntityProjection')
    const projectionMetadataJoinKey = this.projectionMetadata.joinKey as keyof TEntity
    const entityJoinKey = (this.entity as any)[projectionMetadataJoinKey]
    if (!entityJoinKey) {
      logger.warn(
        `Couldn't find the joinKey ${this.projectionMetadata.joinKey} in entity snapshot of ${this.entityMetadata.class.name}. Skipping...`
      )
      return
    }
    const ids = Array.isArray(entityJoinKey) ? entityJoinKey : [entityJoinKey]
    if (!ids || ids.length === 0) {
      return undefined
    }
    return {
      id: {
        in: ids,
      },
    }
  }

  filterForReadModelProjection(): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ProjectFilterFor#filterForReadModelProjection')
    const joinKeyForProjection = this.projectionMetadata.joinKey as ReadModelJoinKeyFunction<
      EntityInterface,
      ReadModelInterface
    >
    if (!joinKeyForProjection) {
      logger.warn(
        `Couldn't find the joinKey ${this.projectionMetadata.joinKey} in entity snapshot of ${this.entityMetadata.class.name}. Skipping...`
      )
      return
    }
    return joinKeyForProjection(this.entity)
  }
}
