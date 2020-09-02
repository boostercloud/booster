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
} from '@boostercloud/framework-types'

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

    await Promise.all(
      projections.map(async (projectionMetadata: ProjectionMetadata) => {
        const readModelName = projectionMetadata.class.name
        const entitySnapshot = entitySnapshotEnvelope.value as EntityInterface
        const readModelID = this.joinKeyForProjection(entitySnapshot, projectionMetadata)
        const readModel = await this.fetchReadModel(readModelName, readModelID)
        this.logger.debug(
          '[ReadModelStore#project] Projecting entity snapshot ',
          entitySnapshotEnvelope,
          ' to build new state of read model ${readModelName} with ID ${readModelID}'
        )
        const newReadModel = this.reducerForProjection(projectionMetadata)(entitySnapshot, readModel)

        if (newReadModel === null) {
          return this.provider.readModels.deleteReadModel(this.config, this.logger, readModelName, readModel)
        }
        this.logger.debug(
          `[ReadModelStore#project] Storing new version of read model ${readModelName} with ID ${readModelID}:`,
          newReadModel
        )
        return this.provider.readModels.store(this.config, this.logger, readModelName, newReadModel)
      })
    )
  }

  public async fetchReadModel(readModelName: string, readModelID: UUID): Promise<ReadModelInterface> {
    this.logger.debug(
      `[ReadModelStore#fetchReadModel] Looking for existing version of read model ${readModelName} with ID ${readModelID}`
    )
    return this.provider.readModels.fetch(this.config, this.logger, readModelName, readModelID)
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

  private reducerForProjection(projectionMetadata: ProjectionMetadata): Function {
    try {
      return (projectionMetadata.class as any)[projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${projectionMetadata.class.name}`)
    }
  }
}
