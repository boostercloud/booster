import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelInterface,
  UUID,
  ProjectionInfo,
  BoosterConfig,
} from '@boostercloud/framework-types'

type PropType<TObj, TProp extends keyof TObj> = TObj[TProp]

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 */
export function Projects<
  TEntity extends EntityInterface,
  TJoinKey extends keyof TEntity,
  TReadModel extends ReadModelInterface
>(
  originEntity: Class<TEntity>,
  joinKey: TJoinKey,
  unProject?: UnprojectionMethod<TEntity, TReadModel, PropType<TEntity, TJoinKey>>
): <TReadModel extends ReadModelInterface>(
  readModelClass: Class<TReadModel>,
  methodName: string,
  methodDescriptor: ProjectionMethod<TEntity, TReadModel, PropType<TEntity, TJoinKey>>
) => void {
  return (readModelClass, methodName) => {
    const projectionMetadata = {
      joinKey,
      class: readModelClass,
      methodName: methodName,
    } as ProjectionMetadata<EntityInterface>
    registerProjection(originEntity.name, projectionMetadata)
    if (unProject) {
      const unProjectionMetadata = {
        joinKey,
        class: readModelClass,
        methodName: unProject.name,
      } as ProjectionMetadata<EntityInterface>
      registerUnProjection(originEntity.name, unProjectionMetadata)
    }
  }
}

function registerProjection(originName: string, projectionMetadata: ProjectionMetadata<EntityInterface>): void {
  Booster.configureCurrentEnv((config): void => {
    configure(config, originName, projectionMetadata, config.projections)
  })
}

function registerUnProjection(originName: string, projectionMetadata: ProjectionMetadata<EntityInterface>): void {
  Booster.configureCurrentEnv((config): void => {
    configure(config, originName, projectionMetadata, config.unProjections)
  })
}

function configure(
  config: BoosterConfig,
  originName: string,
  projectionMetadata: ProjectionMetadata<EntityInterface>,
  configuration: Record<string, Array<ProjectionMetadata<EntityInterface>>>
): void {
  const entityProjections = configuration[originName] || []
  if (entityProjections.indexOf(projectionMetadata) < 0) {
    // Skip duplicate registrations
    entityProjections.push(projectionMetadata)
    configuration[originName] = entityProjections
  }
}

type ProjectionMethodDefinitionForArray<TEntity, TReadModel> = (
  _: TEntity,
  readModelID: UUID,
  readModel?: TReadModel,
  projectionInfo?: ProjectionInfo
) => ProjectionResult<TReadModel>

type ProjectionMethodDefinition<TEntity, TReadModel> = (
  _: TEntity,
  readModel?: TReadModel,
  projectionInfo?: ProjectionInfo
) => ProjectionResult<TReadModel>

type ProjectionMethod<TEntity, TReadModel, TPropType> = TPropType extends Array<UUID>
  ? TypedPropertyDescriptor<ProjectionMethodDefinitionForArray<TEntity, TReadModel>>
  : TypedPropertyDescriptor<ProjectionMethodDefinition<TEntity, TReadModel>>

type UnprojectionMethod<TEntity, TReadModel, TPropType> = TPropType extends Array<UUID>
  ? ProjectionMethodDefinitionForArray<TEntity, TReadModel>
  : ProjectionMethodDefinition<TEntity, TReadModel>
