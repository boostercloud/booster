import {
  BoosterConfig,
  EntityInterface,
  EntityMetadata,
  EntitySnapshotEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionMetadata,
  ReadModelInterface,
  SequenceKey,
  UUID,
} from '@boostercloud/framework-types'
import { createInstance, getLogger, Promises, retryIfError } from '@boostercloud/framework-common-helpers'
import { readModelSearcher } from '../read-model-searcher'
import { isJoinKeyByEntity } from './read-model-project-utils'
import { ProjectFilterFor } from './project-filter-for'
import { ProjectionStore } from './projection-store'

/**
 * Project an entity to all read models that are subscribed to it
 */
export class ProjectEntity {
  private projectFilterFor: ProjectFilterFor

  constructor(
    readonly config: BoosterConfig,
    readonly entityInstance: EntityInterface,
    readonly projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    readonly entityMetadata: EntityMetadata,
    readonly entitySnapshotEnvelope: EntitySnapshotEnvelope,
    readonly readModelName: string,
    readonly sequenceKey?: SequenceKey
  ) {
    this.projectFilterFor = new ProjectFilterFor(config, entityInstance, projectionMetadata, entityMetadata)
  }

  public async project(): Promise<Array<PromiseSettledResult<unknown>> | undefined> {
    const currentReadModels: Array<ReadModelInterface> = await this.getReadModels()
    const projection = []
    if (currentReadModels && currentReadModels.length > 0) {
      const existingReadModelsProjection = await this.projectionsForExistingReadModels(currentReadModels)
      projection.push(...existingReadModelsProjection)
    } else {
      const newReadModelsProjections = await this.projectionsForNewReadModels()
      projection.push(...newReadModelsProjections)
    }

    return Promises.allSettledAndFulfilled(projection)
  }

  async projectionsForExistingReadModels(readModels: Array<ReadModelInterface>): Promise<Array<Promise<unknown>>> {
    return readModels.map((currentReadModel: ReadModelInterface) =>
      // An existing Read Model needs to projected using a JoinKey with a read model query. We have the read model instance and the id
      this.projectAndStoreExistingReadModel(currentReadModel, currentReadModel.id)
    )
  }

  async projectionsForNewReadModels(): Promise<Array<Promise<unknown>>> {
    const projections = []
    const logger = getLogger(this.config, 'ProjectEntity#projectionsForNewReadModels')
    if (isJoinKeyByEntity(this.projectionMetadata.joinKey)) {
      const entityJoinKey = (this.entityInstance as any)[this.projectionMetadata.joinKey]
      if (!entityJoinKey) {
        logger.warn(
          `Couldn't find the joinKey named ${this.projectionMetadata} in entity snapshot of ${this.entityMetadata.class.name}. Skipping...`
        )
        return []
      }
      const entitiesJoinKeys: Array<UUID> = Array.isArray(entityJoinKey) ? entityJoinKey : [entityJoinKey]
      // A new Read Model needs to projected using a JoinKey with an entity query. We don't have a previous read model, but we have the new id from the entity
      entitiesJoinKeys.forEach((readModelId) => {
        projections.push(this.projectAndStoreReadModelById(readModelId))
      })
    } else {
      // A new Read Model needs to projected using a JoinKey with a read model query. We don't have a previous read model nor the new id as we don't have an entity field
      projections.push(this.projectAndStoreNewReadModel())
    }
    return projections
  }

  async projectAndStoreExistingReadModel(readModel: ReadModelInterface, readModelId: UUID): Promise<unknown> {
    return await this.projectAndStoreReadModelWithRetry(readModelId, readModel)
  }

  async projectAndStoreNewReadModel(): Promise<unknown> {
    return await this.projectAndStoreReadModelWithRetry()
  }

  async projectAndStoreReadModelById(readModelId: UUID): Promise<unknown> {
    return await this.projectAndStoreReadModelWithRetry(readModelId)
  }

  async projectAndStoreReadModelWithRetry(readModelId?: UUID, currentReadModel?: ReadModelInterface): Promise<unknown> {
    const logger = getLogger(this.config, 'ProjectEntity#projectAndStoreReadModelWithRetry')
    logger.debug(
      'Projecting entity snapshot ',
      this.entitySnapshotEnvelope,
      ` to build new state of read model ${this.readModelName} with ID ${currentReadModel?.id || readModelId}`,
      this.sequenceKey ? ` sequencing by ${this.sequenceKey.name} with value ${this.sequenceKey.value}` : ''
    )

    const projectionStore = new ProjectionStore(
      this.config,
      this.entityInstance,
      this.projectionMetadata,
      currentReadModel,
      this.entitySnapshotEnvelope,
      readModelId,
      this.sequenceKey
    )
    return retryIfError(
      () => projectionStore.projectNewReadModelAndStore(),
      OptimisticConcurrencyUnexpectedVersionError,
      logger
    )
  }

  async getReadModels(): Promise<Array<ReadModelInterface>> {
    const readModelName = this.projectionMetadata.class.name
    const readModelMetadata = this.config.readModels[readModelName]
    const filter = this.projectFilterFor.filterForProjection()
    if (!filter) {
      return []
    }
    const rawReadModels = (await readModelSearcher<ReadModelInterface>(this.config, readModelMetadata.class)
      .filter(filter)
      .paginatedVersion(false)
      .search()) as Array<ReadModelInterface>
    return this.instanceReadModels(readModelName, rawReadModels)
  }

  instanceReadModels(
    readModelName: string,
    rawReadModels: Array<ReadModelInterface> | undefined
  ): Array<ReadModelInterface> {
    if (!rawReadModels?.length) {
      return []
    }
    const readModelMetadata = this.config.readModels[readModelName]
    return rawReadModels.map((rawReadModel) => createInstance(readModelMetadata.class, rawReadModel))
  }
}
