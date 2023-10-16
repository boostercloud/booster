import {
  BoosterConfig,
  EntityInterface,
  ProjectionGlobalError,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelInterface,
  UUID,
} from '@boostercloud/framework-types'
import { isJoinKeyByEntity } from './read-model-project-utils'
import { BoosterGlobalErrorDispatcher } from '../../booster-global-error-dispatcher'

/**
 * Call the read models projection function
 */
export class ProjectFunction {
  constructor(
    readonly config: BoosterConfig,
    readonly projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    readonly entity: EntityInterface,
    readonly migratedReadModel: ReadModelInterface | undefined,
    readonly readModelID: UUID | undefined
  ) {}

  public async callFunction<TReadModel extends ReadModelInterface>(): Promise<
    ProjectionResult<TReadModel> | undefined
  > {
    try {
      const projectionMetadataJoinKey = this.projectionMetadata.joinKey
      const projectionFunction = this.getProjectionFunction()
      if (isJoinKeyByEntity(projectionMetadataJoinKey)) {
        return Array.isArray(this.entity[projectionMetadataJoinKey])
          ? projectionFunction(this.entity, this.readModelID, this.migratedReadModel || null)
          : projectionFunction(this.entity, this.migratedReadModel || null)
      }
      return projectionFunction(this.entity, this.readModelID, this.migratedReadModel || null)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(
        new ProjectionGlobalError(this.entity, this.migratedReadModel, e)
      )
      if (error) throw error
    }
    return undefined
  }

  // eslint-disable-next-line @typescript-eslint/ban-types
  getProjectionFunction(): Function {
    try {
      return (this.projectionMetadata.class as any)[this.projectionMetadata.methodName]
    } catch {
      throw new Error(`Couldn't load the ReadModel class ${this.projectionMetadata.class.name}`)
    }
  }
}
