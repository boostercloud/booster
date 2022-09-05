/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  EntityInterface,
  EntityMetadata,
  EventEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionGlobalError,
  ProjectionMetadata,
  ReadModelAction,
  ReadModelInterface,
  ReadModelJoinKeyFunction,
  SequenceKey,
  UUID,
} from '@boostercloud/framework-types'
import { createInstance, getLogger, Promises, retryIfError } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from '../booster-global-error-dispatcher'
import { ReadModelSchemaMigrator } from '../read-model-schema-migrator'
import { readModelSearcher } from './read-model-searcher'

export class ReadModelStore {
  public constructor(readonly config: BoosterConfig) {}

  public async project(entitySnapshotEnvelope: EventEnvelope): Promise<void> {
    const logger = getLogger(this.config, 'ReadModelStore#project')
    const projections = this.config.projections[entitySnapshotEnvelope.entityTypeName]
    if (!projections) {
      logger.debug(
        `[ReadModelStore#project] No projections found for entity ${entitySnapshotEnvelope.entityTypeName}. Skipping...`
      )
      return
    }
    const entityMetadata = this.config.entities[entitySnapshotEnvelope.entityTypeName]
    const entityInstance = createInstance(entityMetadata.class, entitySnapshotEnvelope.value)
    const projectReadModelPromises = projections.flatMap(
      (projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>) =>
        this.projectReadModel(projectionMetadata, entityInstance, entitySnapshotEnvelope, entityMetadata)
    )
    await Promises.allSettledAndFulfilled(projectReadModelPromises)
  }

  private async projectReadModel(
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entityInstance: EntityInterface,
    entitySnapshotEnvelope: EventEnvelope,
    entityMetadata: EntityMetadata
  ): Promise<void> {
    const logger = getLogger(this.config, 'ReadModelStore#projectReadModel')
    const readModelIDList = await this.joinKeyForProjection(entityInstance, projectionMetadata)
    if (!readModelIDList) {
      logger.warn(
        `Couldn't find the joinKey named ${projectionMetadata.joinKey} in entity snapshot of ${entityMetadata.class.name}. Skipping...`
      )
      return
    }

    const readModelName = projectionMetadata.class.name
    const sequenceKey = this.sequenceKeyForProjection(entityInstance, projectionMetadata)
    const projectionByIdPromises = readModelIDList.flatMap((readModelID: UUID) => {
      logger.debug(
        'Projecting entity snapshot ',
        entitySnapshotEnvelope,
        ` to build new state of read model ${readModelName} with ID ${readModelID}`,
        sequenceKey ? ` sequencing by ${sequenceKey.name} with value ${sequenceKey.value}` : ''
      )

      return retryIfError(
        () =>
          this.applyProjectionToReadModel(entityInstance, projectionMetadata, readModelName, readModelID, sequenceKey),
        OptimisticConcurrencyUnexpectedVersionError,
        logger
      )
    })
    await Promises.allSettledAndFulfilled(projectionByIdPromises)
  }

  private async joinKeyForProjection(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>
  ): Promise<Array<UUID> | undefined> {
    if (typeof projectionMetadata.joinKey === 'string') {
      const entityJoinKey = (entity as any)[projectionMetadata.joinKey]
      if (!entityJoinKey) {
        return undefined
      }
      return Array.isArray(entityJoinKey) ? entityJoinKey : [entityJoinKey]
    }
    return this.getReadModelIdList(projectionMetadata.joinKey, projectionMetadata.class.name, entity)
  }

  private async getReadModelIdList(
    joinKeyForProjection: ReadModelJoinKeyFunction<EntityInterface, ReadModelInterface>,
    readModelName: string,
    entityInstance: EntityInterface
  ): Promise<Array<UUID>> {
    const logger = getLogger(this.config, 'ReadModelStore#getReadModelIdList')
    logger.debug(`Looking for ReadModels using FilterFor ${joinKeyForProjection}`)
    const readModelMetadata = this.config.readModels[readModelName]
    const filter = joinKeyForProjection(entityInstance)
    if (!filter) {
      return []
    }
    const readModels = (await readModelSearcher<ReadModelInterface>(this.config, readModelMetadata.class)
      .filter(filter)
      .paginatedVersion(false)
      .search()) as Array<ReadModelInterface>
    return readModels.map((readModel) => readModel.id)
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

  private async applyProjectionToReadModel(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'ReadModelStore#applyProjectionToReadModel')
    const readModel = await this.fetchReadModel(readModelName, readModelID, sequenceKey)
    let migratedReadModel: ReadModelInterface | undefined
    if (readModel) {
      migratedReadModel = await new ReadModelSchemaMigrator(this.config).migrate(readModel, readModelName)
    }
    const currentReadModelVersion: number = migratedReadModel?.boosterMetadata?.version ?? 0

    let newReadModel: any
    try {
      if (typeof projectionMetadata.joinKey === 'string') {
        newReadModel = Array.isArray(entity[projectionMetadata.joinKey])
          ? this.projectionFunction(projectionMetadata)(entity, readModelID, migratedReadModel || null)
          : this.projectionFunction(projectionMetadata)(entity, migratedReadModel || null)
      } else {
        newReadModel = this.projectionFunction(projectionMetadata)(entity, readModelID, migratedReadModel || null)
      }
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(new ProjectionGlobalError(entity, migratedReadModel, e))
      if (error) throw error
    }

    if (newReadModel === ReadModelAction.Delete) {
      logger.debug(`Deleting read model ${readModelName} with ID ${readModelID}:`, migratedReadModel)
      return this.config.provider.readModels.delete(this.config, readModelName, migratedReadModel)
    } else if (newReadModel === ReadModelAction.Nothing) {
      logger.debug(
        `[ReadModelStore#project] Skipping actions for ${readModelName} with ID ${readModelID}:`,
        newReadModel
      )
      return
    }
    const schemaVersion: number =
      migratedReadModel?.boosterMetadata?.schemaVersion ?? this.config.currentVersionFor(readModelName)
    // Increment the read model version in 1 before storing
    newReadModel.boosterMetadata = {
      ...migratedReadModel?.boosterMetadata,
      version: currentReadModelVersion + 1,
      schemaVersion: schemaVersion,
    }
    logger.debug(
      `[ReadModelStore#project] Storing new version of read model ${readModelName} with ID ${readModelID}:`,
      newReadModel
    )

    return this.config.provider.readModels.store(this.config, readModelName, newReadModel, currentReadModelVersion)
  }

  /**
   * Gets a specific read model instance referencing it by ID when it's a regular read model
   * or by ID + sequenceKey when it's a sequenced read model
   */
  public async fetchReadModel(
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<ReadModelInterface | undefined> {
    const logger = getLogger(this.config, 'ReadModelStore#fetchReadModel')
    logger.debug(
      `[ReadModelStore#fetchReadModel] Looking for existing version of read model ${readModelName} with ID = ${readModelID}` +
        (sequenceKey ? ` and sequence key ${sequenceKey.name} = ${sequenceKey.value}` : '')
    )
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
  public projectionFunction(projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>): Function {
    try {
      return (projectionMetadata.class as any)[projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${projectionMetadata.class.name}`)
    }
  }
}
