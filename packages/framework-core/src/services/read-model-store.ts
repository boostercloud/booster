/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  BoosterMetadata,
  EntityInterface,
  EntityMetadata,
  EntitySnapshotEnvelope,
  FilterFor,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionGlobalError,
  ProjectionInfo,
  ProjectionInfoReason,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelAction,
  ReadModelInterface,
  ReadModelJoinKeyFunction,
  SequenceKey,
  UUID,
} from '@boostercloud/framework-types'
import { createInstance, getLogger, Promises, retryIfError } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from '../booster-global-error-dispatcher'
import { readModelSearcher } from './read-model-searcher'
import { ReadModelSchemaMigrator } from '../read-model-schema-migrator'

export class ReadModelStore {
  public constructor(readonly config: BoosterConfig) {}

  public async project(entitySnapshotEnvelope: EntitySnapshotEnvelope, deleteEvent = false): Promise<void> {
    const logger = getLogger(this.config, 'ReadModelStore#project')
    const projections = deleteEvent
      ? this.getUnProjections(entitySnapshotEnvelope)
      : this.entityProjections(entitySnapshotEnvelope)
    if (!projections) {
      logger.debug(`No projections found for entity ${entitySnapshotEnvelope.entityTypeName}. Skipping...`)
      return
    }
    logger.debug(
      `Projections found for entity ${entitySnapshotEnvelope.entityTypeName}: ${JSON.stringify(projections)}`
    )
    const entityMetadata = this.config.entities[entitySnapshotEnvelope.entityTypeName]
    const entityInstance = createInstance(entityMetadata.class, entitySnapshotEnvelope.value)
    const projectReadModelPromises = projections.flatMap(
      (projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>) => {
        logger.debug(
          `Projecting entity snapshot ${entitySnapshotEnvelope} to build new state of read model with projectionMetadata ${JSON.stringify(
            projectionMetadata
          )}`
        )
        const readModelName = projectionMetadata.class.name
        const sequenceKey = this.sequenceKeyForProjection(entityInstance, projectionMetadata)
        return this.projectEntity(
          entityInstance,
          projectionMetadata,
          entityMetadata,
          entitySnapshotEnvelope,
          readModelName,
          deleteEvent,
          sequenceKey
        )
      }
    )
    await Promises.allSettledAndFulfilled(projectReadModelPromises)
  }

  /**
   * Gets the read models for a given entity instance using the projection metadata
   * @param {EntityInterface} entityInstance The entity instance to get the read models for
   * @param {ProjectionMetadata<EntityInterface, ReadModelInterface>} projectionMetadata The projection metadata to use to get the read models
   * @param {EntityMetadata} entityMetadata The entity metadata for the entity instance
   * @returns {Promise<Array<ReadModelInterface>>} The read models for the entity instance
   */
  public async getReadModels(
    entityInstance: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityMetadata: EntityMetadata
  ): Promise<Array<ReadModelInterface>> {
    const logger = getLogger(this.config, 'ReadModelStore#getReadModels')
    logger.debug(
      `Looking for ReadModels for entity ${JSON.stringify(entityInstance)} using Filter ${projectionMetadata.joinKey}`
    )
    const readModelName = projectionMetadata.class.name
    const readModelMetadata = this.config.readModels[readModelName]
    const filter = this.filterForProjection(entityInstance, projectionMetadata, entityMetadata)
    if (!filter) {
      return []
    }
    logger.debug(
      `Calling ReadModelSearcher searching for ReadModels for entity ${readModelMetadata.class.name} using Filter ${filter}`
    )
    const rawReadModels = (await readModelSearcher<ReadModelInterface>(this.config, readModelMetadata.class)
      .filter(filter)
      .paginatedVersion(false)
      .search()) as Array<ReadModelInterface>
    return this.instanceReadModels(readModelName, rawReadModels)
  }

  /**
   * Gets a specific read model instance referencing it by ID when it's a regular read model
   * or by ID + sequenceKey when it's a sequenced read model
   * @param {string} readModelName The name of the read model class
   * @param {UUID | undefined} readModelID The ID of the read model instance
   * @param {SequenceKey} sequenceKey The sequence key of the read model instance
   * @returns {Promise<ReadModelInterface | undefined>} The read model instance or undefined if it doesn't exist
   */
  async fetchReadModel(
    readModelName: string,
    readModelID: UUID | undefined,
    sequenceKey?: SequenceKey
  ): Promise<ReadModelInterface | undefined> {
    if (!readModelID) {
      return undefined
    }
    const rawReadModels = await this.config.provider.readModels.fetch(
      this.config,
      readModelName,
      readModelID,
      sequenceKey
    )
    if (rawReadModels?.length) {
      if (rawReadModels.length > 1) {
        throw 'Got multiple objects for a request by Id. If this is a sequenced read model you should also specify the sequenceKey field.'
      } else if (rawReadModels.length === 1 && rawReadModels[0]) {
        const readModelMetadata = this.config.readModels[readModelName]
        return createInstance(readModelMetadata.class, rawReadModels[0])
      }
    }
    return undefined
  }

  // eslint-disable-next-line @typescript-eslint/ban-types
  getProjectionFunction(projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>): Function {
    try {
      return (projectionMetadata.class as any)[projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${projectionMetadata.class.name}`)
    }
  }

  private getUnProjections(
    entitySnapshotEnvelope: EntitySnapshotEnvelope
  ): Array<ProjectionMetadata<EntityInterface, ReadModelInterface>> {
    const unProjections: Array<ProjectionMetadata<EntityInterface, ReadModelInterface>> =
      this.entityUnProjections(entitySnapshotEnvelope)
    const projections: Array<ProjectionMetadata<EntityInterface, ReadModelInterface>> =
      this.entityProjections(entitySnapshotEnvelope)
    if (projections?.length > 0) {
      if (!unProjections) {
        throw new Error(`Missing UnProjections for entity ${entitySnapshotEnvelope.entityTypeName}`)
      }
      const missingProjection = this.findFirstMissingProjection(projections, unProjections)
      if (missingProjection) {
        throw new Error(
          `Missing UnProjection for ReadModel ${missingProjection.class.name} with joinKey ${missingProjection.joinKey} for entity ${entitySnapshotEnvelope.entityTypeName}`
        )
      }
    }
    return unProjections
  }

  private entityProjections(
    entitySnapshotEnvelope: EntitySnapshotEnvelope
  ): Array<ProjectionMetadata<EntityInterface, ReadModelInterface>> {
    return this.config.projections[entitySnapshotEnvelope.entityTypeName]
  }

  private entityUnProjections(
    entitySnapshotEnvelope: EntitySnapshotEnvelope
  ): Array<ProjectionMetadata<EntityInterface, ReadModelInterface>> {
    return this.config.unProjections[entitySnapshotEnvelope.entityTypeName]
  }

  private findFirstMissingProjection(
    sources: Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>,
    to: Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>
  ): ProjectionMetadata<EntityInterface, ReadModelInterface> | undefined {
    return sources.find(
      (source: ProjectionMetadata<EntityInterface, ReadModelInterface>) => !this.someProjection(to, source)
    )
  }

  private someProjection(
    sources: Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>,
    to: ProjectionMetadata<EntityInterface, ReadModelInterface>
  ): boolean {
    const contains = (source: ProjectionMetadata<EntityInterface, ReadModelInterface>) =>
      source.class.name === to.class.name && source.joinKey.toString() === to.joinKey.toString()
    return sources.some(contains)
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

  private async projectEntity(
    entityInstance: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityMetadata: EntityMetadata,
    entitySnapshotEnvelope: EntitySnapshotEnvelope,
    readModelName: string,
    deleteEvent: boolean,
    sequenceKey?: SequenceKey
  ): Promise<Array<PromiseSettledResult<unknown>> | undefined> {
    const currentReadModels: Array<ReadModelInterface> = await this.getReadModels(
      entityInstance,
      projectionMetadata,
      entityMetadata
    )
    if (currentReadModels && currentReadModels.length > 0) {
      const existingReadModelsProjections: Array<Promise<unknown>> = []
      for (const currentReadModel of currentReadModels) {
        const newProjections: Array<Promise<unknown>> = await this.projectionsForReadModels(
          entitySnapshotEnvelope,
          readModelName,
          sequenceKey,
          projectionMetadata,
          entityInstance,
          entityMetadata,
          deleteEvent,
          currentReadModel
        )
        existingReadModelsProjections.push(...newProjections)
      }
      return Promises.allSettledAndFulfilled(existingReadModelsProjections)
    }
    const newProjections: Array<Promise<unknown>> = await this.projectionsForReadModels(
      entitySnapshotEnvelope,
      readModelName,
      sequenceKey,
      projectionMetadata,
      entityInstance,
      entityMetadata,
      deleteEvent
    )
    return Promises.allSettledAndFulfilled(newProjections)
  }

  private async projectionsForReadModels(
    entitySnapshotEnvelope: EntitySnapshotEnvelope,
    readModelName: string,
    sequenceKey: any,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityInstance: EntityInterface,
    entityMetadata: EntityMetadata,
    deleteEvent: boolean,
    currentReadModel?: ReadModelInterface
  ): Promise<Array<Promise<unknown>>> {
    const projections: Array<Promise<unknown>> = []
    const logger = getLogger(this.config, 'ReadModelStore#projectionsForReadModels')
    if (this.isJoinKeyByEntity(projectionMetadata.joinKey)) {
      const entityJoinKey = (entityInstance as any)[projectionMetadata.joinKey]
      if (!entityJoinKey) {
        logger.warn(
          `Couldn't find the joinKey named ${projectionMetadata} in entity snapshot of ${entityMetadata.class.name}. Skipping...`
        )
        return []
      }
      const entitiesJoinKeys: Array<UUID> = Array.isArray(entityJoinKey) ? entityJoinKey : [entityJoinKey]
      // A new Read Model with JoinKey by entity needs to be projected using a JoinKey with an entity query. We don't have a previous read model, but we have the new id from the entity
      for (const readModelId of entitiesJoinKeys) {
        const readModel = currentReadModel?.id === readModelId ? currentReadModel : undefined
        projections.push(
          this.projectAndStoreReadModelWithRetry(
            entitySnapshotEnvelope,
            readModelName,
            sequenceKey,
            entityInstance,
            projectionMetadata,
            deleteEvent,
            readModelId,
            readModel
          )
        )
      }
    } else {
      // A new Read Model with JoinKey by ReadModel needs to be projected using a JoinKey with a read model query. We don't have a previous read model nor the new id as we don't have an entity field
      projections.push(
        this.projectAndStoreReadModelWithRetry(
          entitySnapshotEnvelope,
          readModelName,
          sequenceKey,
          entityInstance,
          projectionMetadata,
          deleteEvent,
          currentReadModel?.id,
          currentReadModel
        )
      )
    }
    return projections
  }

  private async projectAndStoreReadModelWithRetry(
    entitySnapshotEnvelope: EntitySnapshotEnvelope,
    readModelName: string,
    sequenceKey: SequenceKey | undefined,
    entityInstance: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    deleteEvent: boolean,
    readModelId?: UUID,
    currentReadModel?: ReadModelInterface
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'ReadModelStore#projectAndStoreReadModelWithRetry')
    logger.debug(
      'Projecting entity snapshot ',
      entitySnapshotEnvelope,
      ` to build new state of read model ${readModelName} with ID ${currentReadModel?.id || readModelId}`,
      sequenceKey ? ` sequencing by ${sequenceKey.name} with value ${sequenceKey.value}` : ''
    )

    return retryIfError(
      (tryNumber?: number) =>
        this.applyProjectionToReadModel(
          entitySnapshotEnvelope,
          entityInstance,
          projectionMetadata,
          deleteEvent,
          currentReadModel,
          entitySnapshotEnvelope,
          readModelId,
          sequenceKey,
          tryNumber
        ),
      OptimisticConcurrencyUnexpectedVersionError,
      logger
    )
  }

  private instanceReadModels(
    readModelName: string,
    rawReadModels: Array<ReadModelInterface> | undefined
  ): Array<ReadModelInterface> {
    if (!rawReadModels?.length) {
      return []
    }
    const readModelMetadata = this.config.readModels[readModelName]
    return rawReadModels.map((rawReadModel) => createInstance(readModelMetadata.class, rawReadModel))
  }

  private async applyProjectionToReadModel(
    entitySnapshotEnvelope: EntitySnapshotEnvelope,
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    deleteEvent: boolean,
    currentReadModel?: ReadModelInterface,
    lastProjectedEntity?: EntitySnapshotEnvelope,
    currentReadModelID?: UUID,
    sequenceKey?: SequenceKey,
    tryNumber?: number
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'ReadModelStore#applyProjectionToReadModel')
    const readModelName = projectionMetadata.class.name
    const readModelID = currentReadModelID ?? currentReadModel?.id

    if (tryNumber && tryNumber > 1) {
      // In case of optimistic concurrency error, we need to fetch the current read model version and retry
      logger.debug(
        `OptimisticConcurrencyUnexpectedVersionError (version=${
          currentReadModel?.boosterMetadata?.version
        } and expectedDatabaseVersion=${
          currentReadModel?.boosterMetadata?.version ?? 0
        }). Looking for an updated version of read model ${readModelName} with ID = ${readModelID}` +
          (sequenceKey ? ` and sequence key ${sequenceKey.name} = ${sequenceKey.value}` : '')
      )

      currentReadModel = await this.fetchReadModel(readModelName, readModelID, sequenceKey)
      logger.debug(
        `Current read model ${readModelName} with ID ${readModelID} updated with version = ${currentReadModel?.boosterMetadata?.version}` +
          (sequenceKey ? ` and sequence key ${sequenceKey.name} = ${sequenceKey.value}` : '')
      )
    }
    let migratedReadModel: ReadModelInterface | undefined
    if (currentReadModel) {
      migratedReadModel = await new ReadModelSchemaMigrator(this.config).migrate(currentReadModel, readModelName)
    }
    const currentDatabaseVersion: number = migratedReadModel?.boosterMetadata?.version ?? 0

    let newReadModel: any
    const projectionInfo: ProjectionInfo = {
      reason: deleteEvent ? ProjectionInfoReason.ENTITY_DELETED : ProjectionInfoReason.ENTITY_PROJECTED,
    }
    try {
      newReadModel = await this.callProjectionFunction(
        entitySnapshotEnvelope,
        projectionMetadata,
        entity,
        migratedReadModel,
        readModelID,
        projectionInfo
      )
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(
        new ProjectionGlobalError(entitySnapshotEnvelope, entity, migratedReadModel, projectionMetadata, e)
      )
      if (error) throw error
    }

    if (newReadModel === ReadModelAction.Delete) {
      logger.debug(`Deleting read model ${readModelName} with ID ${readModelID}:`, migratedReadModel)
      return this.config.provider.readModels.delete(this.config, readModelName, migratedReadModel)
    } else if (newReadModel === ReadModelAction.Nothing) {
      logger.debug(`Skipping actions for ${readModelName} with ID ${readModelID}:`, newReadModel)
      return
    }

    return await this.store(
      entity,
      projectionMetadata,
      readModelID,
      readModelName,
      migratedReadModel,
      newReadModel,
      currentDatabaseVersion,
      lastProjectedEntity
    )
  }

  private async store(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    readModelID: UUID | undefined,
    readModelName: string,
    migratedReadModel: ReadModelInterface | undefined,
    newReadModel: any,
    expectedCurrentDatabaseVersion: number,
    lastProjectedEntity?: EntitySnapshotEnvelope
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'ReadModelStore#store')
    const schemaVersion: number =
      migratedReadModel?.boosterMetadata?.schemaVersion ?? this.config.currentVersionFor(readModelName)
    // Increment the read model version in 1 before storing
    const newReadModelVersion = expectedCurrentDatabaseVersion + 1
    newReadModel.boosterMetadata = {
      ...migratedReadModel?.boosterMetadata,
      version: newReadModelVersion,
      schemaVersion: schemaVersion,
      lastUpdateAt: new Date().toISOString(),
      lastProjectionInfo: {
        entityId: entity.id,
        entityName: lastProjectedEntity?.entityTypeName,
        entityUpdatedAt: lastProjectedEntity?.createdAt,
        projectionMethod: `${projectionMetadata.class.name}.${projectionMetadata.methodName}`,
      },
    } as BoosterMetadata
    logger.debug(
      `Storing new version of read model ${readModelName} with ID ${readModelID}, version ${newReadModel.boosterMetadata.version} and expected database version ${expectedCurrentDatabaseVersion}:`,
      newReadModel
    )
    return this.config.provider.readModels.store(
      this.config,
      readModelName,
      newReadModel,
      expectedCurrentDatabaseVersion
    )
  }

  private async callProjectionFunction<TReadModel extends ReadModelInterface>(
    entitySnapshotEnvelope: EntitySnapshotEnvelope,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entity: EntityInterface,
    migratedReadModel: ReadModelInterface | undefined,
    readModelID: UUID | undefined,
    projectionInfo: ProjectionInfo
  ): Promise<ProjectionResult<TReadModel> | undefined> {
    try {
      const projectionMetadataJoinKey = projectionMetadata.joinKey
      const projectionFunction = this.getProjectionFunction(projectionMetadata)
      if (this.isJoinKeyByEntity(projectionMetadataJoinKey)) {
        return Array.isArray(entity[projectionMetadataJoinKey])
          ? projectionFunction(entity, readModelID, migratedReadModel || null, projectionInfo)
          : projectionFunction(entity, migratedReadModel || null, projectionInfo)
      }
      return projectionFunction(entity, readModelID, migratedReadModel || null)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(
        new ProjectionGlobalError(entitySnapshotEnvelope, entity, migratedReadModel, projectionMetadata, e)
      )
      if (error) throw error
    }
    return undefined
  }

  private filterForProjection(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityMetadata: EntityMetadata
  ): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ReadModelStore#filterForProjection')
    const projectionMetadataJoinKey = projectionMetadata.joinKey
    logger.debug(`Calculating filter for projection for ReadModels using Filter ${projectionMetadataJoinKey}`)
    if (this.isJoinKeyByEntity(projectionMetadataJoinKey)) {
      return this.filterForEntityProjection(entity, projectionMetadata, entityMetadata)
    }

    return this.filterForReadModelProjection(entity, projectionMetadata, entityMetadata)
  }

  private filterForEntityProjection<TEntity extends EntityInterface>(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityMetadata: EntityMetadata
  ): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ReadModelStore#filterForEntityProjection')
    const projectionMetadataJoinKey = projectionMetadata.joinKey as keyof TEntity
    const entityJoinKey = (entity as any)[projectionMetadataJoinKey]
    if (!entityJoinKey) {
      logger.warn(
        `Couldn't find the joinKey ${projectionMetadata.joinKey} in entity snapshot of ${entityMetadata.class.name}. Skipping...`
      )
      return
    }
    const ids = Array.isArray(entityJoinKey) ? entityJoinKey : [entityJoinKey]
    if (!ids || ids.length === 0) {
      logger.debug('No ids found for entity projection. Skipping...')
      return undefined
    }
    logger.debug(`Filtering for entity projection with ids ${ids}`)
    return {
      id: {
        in: ids,
      },
    }
  }

  private filterForReadModelProjection(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityMetadata: EntityMetadata
  ): FilterFor<ReadModelInterface> | undefined {
    const logger = getLogger(this.config, 'ReadModelStore#filterForReadModelProjection')
    const joinKeyForProjection = projectionMetadata.joinKey as ReadModelJoinKeyFunction<
      EntityInterface,
      ReadModelInterface
    >
    if (!joinKeyForProjection) {
      logger.warn(
        `Couldn't find the joinKey ${projectionMetadata.joinKey} in entity snapshot of ${entityMetadata.class.name}. Skipping...`
      )
      return
    }
    return joinKeyForProjection(entity)
  }

  private isJoinKeyByEntity<TEntity extends EntityInterface, TReadModel extends ReadModelInterface>(
    projectionMetadataJoinKey: keyof TEntity | ReadModelJoinKeyFunction<TEntity, TReadModel>
  ): projectionMetadataJoinKey is keyof TEntity {
    return typeof projectionMetadataJoinKey === 'string'
  }
}
