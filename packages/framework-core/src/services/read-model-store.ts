/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  Logger,
  ProviderLibrary,
  ReadModelInterface,
  EventEnvelope,
  ProjectionMetadata,
  UUID,
  EntityInterface,
  InvalidParameterError,
  ReadModelAction,
  OptimisticConcurrencyUnexpectedVersionError,
  SequenceKey,
} from '@boostercloud/framework-types'
import { Promises, retryIfError, createInstance } from '@boostercloud/framework-common-helpers'

export class ReadModelStore {
  private config: BoosterConfig
  private provider: ProviderLibrary
  private logger: Logger

  public constructor(config: BoosterConfig, logger: Logger) {
    this.config = config
    this.provider = config.provider
    this.logger = logger
  }

  public async project(entitySnapshotEnvelope: EventEnvelope): Promise<void> {
    const projections = this.config.projections[entitySnapshotEnvelope.entityTypeName]
    if (!projections) {
      this.logger.debug(
        `[ReadModelStore#project] No projections found for entity ${entitySnapshotEnvelope.entityTypeName}. Skipping...`
      )
      return
    }
    const entityMetadata = this.config.entities[entitySnapshotEnvelope.entityTypeName]
    await Promises.allSettledAndFulfilled(
      projections.map(async (projectionMetadata: ProjectionMetadata) => {
        const readModelName = projectionMetadata.class.name
        const entityInstance = createInstance(entityMetadata.class, entitySnapshotEnvelope.value)
        const readModelID = this.joinKeyForProjection(entityInstance, projectionMetadata)
        const sequenceKey = this.sequenceKeyForProjection(entityInstance, projectionMetadata)
        this.logger.debug(
          '[ReadModelStore#project] Projecting entity snapshot ',
          entitySnapshotEnvelope,
          ` to build new state of read model ${readModelName} with ID ${readModelID}`,
          sequenceKey ? ` sequencing by ${sequenceKey.name} with value ${sequenceKey.value}` : ''
        )

        return await retryIfError(
          this.logger,
          () =>
            this.applyProjectionToReadModel(
              entityInstance,
              projectionMetadata,
              readModelName,
              readModelID,
              sequenceKey
            ),
          OptimisticConcurrencyUnexpectedVersionError
        )
      })
    )
  }

  private joinKeyForProjection(entity: EntityInterface, projectionMetadata: ProjectionMetadata): UUID {
    const joinKey = (entity as any)[projectionMetadata.joinKey]
    if (!joinKey) {
      throw new InvalidParameterError(
        `Couldn't find the joinKey named ${projectionMetadata.joinKey} in entity snapshot: ${entity}`
      )
    }
    return joinKey
  }

  private sequenceKeyForProjection(
    entity: EntityInterface,
    projectionMetadata: ProjectionMetadata
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
    projectionMetadata: ProjectionMetadata,
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<unknown> {
    const readModel = await this.fetchReadModel(readModelName, readModelID, sequenceKey)
    const currentReadModelVersion: number = readModel?.boosterMetadata?.version ?? 0
    const newReadModel = this.projectionFunction(projectionMetadata)(entity, readModel)

    if (newReadModel === ReadModelAction.Delete) {
      this.logger.debug(
        `[ReadModelDelete#project] Deleting read model ${readModelName} with ID ${readModelID}:`,
        readModel
      )
      return this.provider.readModels.delete(this.config, this.logger, readModelName, readModel)
    } else if (newReadModel === ReadModelAction.Nothing) {
      this.logger.debug(
        `[ReadModelStore#project] Skipping actions for ${readModelName} with ID ${readModelID}:`,
        newReadModel
      )
      return
    }
    // Increment the read model version in 1 before storing
    newReadModel.boosterMetadata = {
      ...newReadModel.boosterMetadata,
      version: currentReadModelVersion + 1,
    }
    this.logger.debug(
      `[ReadModelStore#project] Storing new version of read model ${readModelName} with ID ${readModelID}:`,
      newReadModel
    )

    return this.provider.readModels.store(
      this.config,
      this.logger,
      readModelName,
      newReadModel,
      currentReadModelVersion
    )
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
    this.logger.debug(
      `[ReadModelStore#fetchReadModel] Looking for existing version of read model ${readModelName} with ID = ${readModelID}` +
        (sequenceKey ? ` and sequence key ${sequenceKey.name} = ${sequenceKey.value}` : '')
    )
    const rawReadModels = await this.provider.readModels.fetch(
      this.config,
      this.logger,
      readModelName,
      readModelID,
      sequenceKey
    )
    if (rawReadModels?.length) {
      if (rawReadModels.length > 1) {
        throw 'Got multiple objects for a request by Id. If this is a sequenced read model you should also specify the sequenceKey field.'
      } else if (rawReadModels.length === 1) {
        const readModelMetadata = this.config.readModels[readModelName]
        return createInstance(readModelMetadata.class, rawReadModels[0])
      }
    }
    return undefined
  }

  public projectionFunction(projectionMetadata: ProjectionMetadata): Function {
    try {
      return (projectionMetadata.class as any)[projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${projectionMetadata.class.name}`)
    }
  }
}
