import {
  BoosterConfig,
  BoosterMetadata,
  EntityInterface,
  EntitySnapshotEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  ProjectionGlobalError,
  ProjectionMetadata,
  ReadModelAction,
  ReadModelInterface,
  SequenceKey,
  UUID,
} from '@boostercloud/framework-types'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { ReadModelSchemaMigrator } from '../../read-model-schema-migrator'
import { BoosterGlobalErrorDispatcher } from '../../booster-global-error-dispatcher'
import { ProjectFunction } from './project-function'

/**
 * Call the projection function and store the result
 */
export class ProjectionStore {
  private readModelID: UUID | undefined
  private readModelName: string

  constructor(
    readonly config: BoosterConfig,
    readonly entity: EntityInterface,
    readonly projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    public currentReadModel?: ReadModelInterface,
    readonly lastProjectedEntity?: EntitySnapshotEnvelope,
    readonly currentReadModelID?: UUID,
    readonly sequenceKey?: SequenceKey
  ) {
    this.readModelName = this.projectionMetadata.class.name
    this.readModelID = this.currentReadModelID ?? this.currentReadModel?.id
  }

  public async projectNewReadModelAndStore(): Promise<unknown> {
    const logger = getLogger(this.config, 'ProjectionStore#projectNewReadModelAndStore')

    let migratedReadModel: ReadModelInterface | undefined
    if (this.currentReadModel) {
      migratedReadModel = await new ReadModelSchemaMigrator(this.config).migrate(
        this.currentReadModel,
        this.readModelName
      )
    }
    const expectedDatabaseVersion: number = migratedReadModel?.boosterMetadata?.version ?? 0

    let newReadModel: any
    try {
      const projectFunction = new ProjectFunction(
        this.config,
        this.projectionMetadata,
        this.entity,
        migratedReadModel,
        this.readModelID
      )
      newReadModel = await projectFunction.callFunction()
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(new ProjectionGlobalError(this.entity, migratedReadModel, e))
      if (error) throw error
    }

    if (newReadModel === ReadModelAction.Delete) {
      logger.debug(`Deleting read model ${this.readModelName} with ID ${this.readModelID}:`, migratedReadModel)
      return this.config.provider.readModels.delete(this.config, this.readModelName, migratedReadModel)
    } else if (newReadModel === ReadModelAction.Nothing) {
      logger.debug(`Skipping actions for ${this.readModelName} with ID ${this.readModelID}:`, newReadModel)
      return
    }

    try {
      return await this.store(migratedReadModel, newReadModel, expectedDatabaseVersion)
    } catch (e) {
      if (e instanceof OptimisticConcurrencyUnexpectedVersionError) {
        // In case of optimistic concurrency error, we need to fetch the current read model version and retry
        logger.debug(
          `OptimisticConcurrencyUnexpectedVersionError. Looking for an updated version of read model ${this.readModelName} with ID = ${this.readModelID}` +
            (this.sequenceKey ? ` and sequence key ${this.sequenceKey.name} = ${this.sequenceKey.value}` : '')
        )

        this.currentReadModel = await this.fetchReadModel()
        logger.debug(
          `Current read model ${this.readModelName} with ID ${this.readModelID} updated with version = ${this.currentReadModel?.boosterMetadata?.version}` +
            (this.sequenceKey ? ` and sequence key ${this.sequenceKey.name} = ${this.sequenceKey.value}` : '')
        )
      }
      throw e
    }
  }

  private async store(
    migratedReadModel: ReadModelInterface | undefined,
    newReadModel: any,
    expectedDatabaseVersion: number
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'ProjectionStore#store')
    const schemaVersion: number =
      migratedReadModel?.boosterMetadata?.schemaVersion ?? this.config.currentVersionFor(this.readModelName)
    // Increment the read model version in 1 before storing
    const newReadModelVersion = expectedDatabaseVersion + 1
    newReadModel.boosterMetadata = {
      ...migratedReadModel?.boosterMetadata,
      version: newReadModelVersion,
      schemaVersion: schemaVersion,
      lastUpdateAt: new Date().toISOString(),
      lastProjectionInfo: {
        entityId: this.entity.id,
        entityName: this.lastProjectedEntity?.entityTypeName,
        entityUpdatedAt: this.lastProjectedEntity?.createdAt,
        projectionMethod: `${this.projectionMetadata.class.name}.${this.projectionMetadata.methodName}`,
      },
    } as BoosterMetadata
    logger.debug(
      `Storing new version of read model ${this.readModelName} with ID ${this.readModelID}, version ${newReadModel.boosterMetadata.version} and expected database version ${expectedDatabaseVersion}:`,
      newReadModel
    )
    return this.config.provider.readModels.store(this.config, this.readModelName, newReadModel, expectedDatabaseVersion)
  }

  /**
   * Gets a specific read model instance referencing it by ID when it's a regular read model
   * or by ID + sequenceKey when it's a sequenced read model
   */
  private async fetchReadModel(): Promise<ReadModelInterface | undefined> {
    if (!this.readModelID) {
      return undefined
    }
    const rawReadModels = await this.config.provider.readModels.fetch(
      this.config,
      this.readModelName,
      this.readModelID,
      this.sequenceKey
    )
    if (rawReadModels?.length) {
      if (rawReadModels.length > 1) {
        throw 'Got multiple objects for a request by Id. If this is a sequenced read model you should also specify the sequenceKey field.'
      } else if (rawReadModels.length === 1 && rawReadModels[0]) {
        const readModelMetadata = this.config.readModels[this.readModelName]
        return createInstance(readModelMetadata.class, rawReadModels[0])
      }
    }
    return undefined
  }
}
