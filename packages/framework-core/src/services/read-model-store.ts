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
} from '@boostercloud/framework-types'
import { Promises, retryIfError } from '@boostercloud/framework-common-helpers'

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
        const entityInstance = new entityMetadata.class()
        Object.assign(entityInstance, entitySnapshotEnvelope.value)
        const readModelID = this.joinKeyForProjection(entityInstance, projectionMetadata)
        this.logger.debug(
          '[ReadModelStore#project] Projecting entity snapshot ',
          entitySnapshotEnvelope,
          ` to build new state of read model ${readModelName} with ID ${readModelID}`
        )

        return await retryIfError(
          this.logger,
          () => this.applyProjectionToReadModel(readModelName, readModelID, projectionMetadata, entityInstance),
          OptimisticConcurrencyUnexpectedVersionError
        )
      })
    )
  }

  private joinKeyForProjection(entitySnapshot: EntityInterface, projectionMetadata: ProjectionMetadata): UUID {
    const joinKey = (entitySnapshot as any)[projectionMetadata.joinKey]
    if (!joinKey) {
      throw new InvalidParameterError(
        `Couldn't find the joinKey named ${projectionMetadata.joinKey} in entity snapshot: ${entitySnapshot}`
      )
    }
    return joinKey
  }

  private async applyProjectionToReadModel(
    readModelName: string,
    readModelID: UUID,
    projectionMetadata: ProjectionMetadata,
    entity: EntityInterface
  ): Promise<unknown> {
    const readModel = await this.fetchReadModel(readModelName, readModelID)
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

  public async fetchReadModel(readModelName: string, readModelID: UUID): Promise<ReadModelInterface> {
    this.logger.debug(
      `[ReadModelStore#fetchReadModel] Looking for existing version of read model ${readModelName} with ID ${readModelID}`
    )
     const rawReadModel = this.provider.readModels.fetch(this.config, this.logger, readModelName, readModelID)
      if (!rawReadModel) {
       return rawReadModel
      }
    const readModelMetadata = this.config.readModels[readModelName]
    const readModelInstance = new readModelMetadata.class()
    void Object.assign(readModelInstance, rawReadModel)
    return readModelInstance
  }

  public projectionFunction(projectionMetadata: ProjectionMetadata): Function {
    try {
      return (projectionMetadata.class as any)[projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${projectionMetadata.class.name}`)
    }
  }
}
