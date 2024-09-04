import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ProjectionInfo,
  ProjectionMetadata,
  ProjectionResult,
  ReadModelInterface,
  ReadModelJoinKeyFunction,
  UUID,
} from '@boostercloud/framework-types'

type PropertyType<TObj, TProp extends keyof TObj> = TObj[TProp]
type JoinKeyType<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> =
  | keyof TEntity
  | ReadModelJoinKeyFunction<TEntity, TReadModel>
type UUIDLike = string | UUID

/**
 * Decorator to register a read model method as a projection
 * for a specific entity
 *
 * @param originEntity The entity that this method will react to
 * @param joinKey
 * @param unProject
 */
export function Projects<
  TEntity extends EntityInterface,
  TJoinKey extends keyof TEntity,
  TReadModel extends ReadModelInterface
>(
  originEntity: Class<TEntity>,
  joinKey: JoinKeyType<TEntity, TReadModel>,
  unProject?: UnprojectionMethod<TEntity, TReadModel, PropertyType<TEntity, TJoinKey>>
): <TReceivedReadModel extends ReadModelInterface>(
  readModelClass: Class<TReceivedReadModel>,
  methodName: string,
  methodDescriptor: ProjectionMethod<TEntity, TReceivedReadModel, JoinKeyType<TEntity, TReceivedReadModel>>
) => void {
  return (readModelClass, methodName) => {
    const projectionMetadata = {
      joinKey: joinKey,
      class: readModelClass,
      methodName: methodName,
    } as ProjectionMetadata<EntityInterface, ReadModelInterface>
    registerProjection(originEntity.name, projectionMetadata)
    if (unProject) {
      const unProjectionMetadata = {
        joinKey,
        class: readModelClass,
        methodName: unProject.name,
      } as ProjectionMetadata<EntityInterface, ReadModelInterface>
      registerUnProjection(originEntity.name, unProjectionMetadata)
    }
  }
}

function registerProjection(
  originName: string,
  projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>
): void {
  Booster.configureCurrentEnv((config): void => {
    configure(originName, projectionMetadata, config.projections)
  })
}

function registerUnProjection(
  originName: string,
  projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>
): void {
  Booster.configureCurrentEnv((config): void => {
    configure(originName, projectionMetadata, config.unProjections)
  })
}

function configure(
  originName: string,
  projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
  configuration: Record<string, Array<ProjectionMetadata<EntityInterface, ReadModelInterface>>>
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

type ProjectionMethod<
  TEntity extends EntityInterface,
  TReadModel extends ReadModelInterface,
  TJoinKeyType extends JoinKeyType<TEntity, TReadModel>
> = TJoinKeyType extends ReadModelJoinKeyFunction<TEntity, TReadModel>
  ? ProjectionMethodWithEntityConditionalReadModelIdAndReadModel<TEntity, TReadModel>
  : TJoinKeyType extends keyof TEntity
  ? NonNullable<PropertyType<TEntity, TJoinKeyType>> extends Array<UUIDLike>
    ? ProjectionMethodWithEntityReadModelIdAndReadModel<TEntity, TReadModel>
    : ProjectionMethodWithEntityAndReadModel<TEntity, TReadModel>
  : never

type ProjectionMethodWithEntityAndReadModel<TEntity extends EntityInterface, TReadModel extends ReadModelInterface> =
  TypedPropertyDescriptor<(_: TEntity, readModel?: TReadModel) => ProjectionResult<TReadModel>>

type ProjectionMethodWithEntityConditionalReadModelIdAndReadModel<
  TEntity extends EntityInterface,
  TReadModel extends ReadModelInterface
> = TypedPropertyDescriptor<
  (_: TEntity, readModelID: UUID | undefined, readModel?: TReadModel) => ProjectionResult<TReadModel>
>

type ProjectionMethodWithEntityReadModelIdAndReadModel<
  TEntity extends EntityInterface,
  TReadModel extends ReadModelInterface
> = TypedPropertyDescriptor<(_: TEntity, readModelID: UUID, readModel?: TReadModel) => ProjectionResult<TReadModel>>

type UnprojectionMethod<TEntity, TReadModel, TPropType> = TPropType extends Array<UUID>
  ? ProjectionMethodDefinitionForArray<TEntity, TReadModel>
  : ProjectionMethodDefinition<TEntity, TReadModel>
