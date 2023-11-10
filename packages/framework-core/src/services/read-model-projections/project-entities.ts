/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  EntityInterface,
  EntitySnapshotEnvelope,
  ProjectionMetadata,
  ReadModelInterface,
  SequenceKey,
} from '@boostercloud/framework-types'
import { createInstance, getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { ProjectEntity } from './project-entity'

/**
 * Project all entities for the given entity to read models
 */
export class ProjectEntities {
  public constructor(readonly config: BoosterConfig) {}

  public async projectEntities(entitySnapshotEnvelope: EntitySnapshotEnvelope): Promise<void> {
    const logger = getLogger(this.config, 'ProjectEntities#projectEntities')
    const projections = this.config.projections[entitySnapshotEnvelope.entityTypeName]
    if (!projections) {
      logger.debug(`No projections found for entity ${entitySnapshotEnvelope.entityTypeName}. Skipping...`)
      return
    }
    const entityMetadata = this.config.entities[entitySnapshotEnvelope.entityTypeName]
    const entityInstance = createInstance(entityMetadata.class, entitySnapshotEnvelope.value)
    const projectReadModelPromises = projections.flatMap(
      (projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>) => {
        const readModelName = projectionMetadata.class.name
        const sequenceKey = this.sequenceKeyForProjection(entityInstance, projectionMetadata)
        const projectEntity = new ProjectEntity(
          this.config,
          entityInstance,
          projectionMetadata,
          entityMetadata,
          entitySnapshotEnvelope,
          readModelName,
          sequenceKey
        )
        return projectEntity.project()
      }
    )
    await Promises.allSettledAndFulfilled(projectReadModelPromises)
  }

  private sequenceKeyForProjection(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>
  ): SequenceKey | undefined {
    const sequenceKeyName = this.config.readModelSequenceKeys[projectionMetadata.class.name]
    const sequenceKeyValue = (entity as any)[sequenceKeyName]
    if (sequenceKeyName && sequenceKeyValue) {
      return { name: sequenceKeyName, value: sequenceKeyValue }
    }
    return undefined
  }
}
