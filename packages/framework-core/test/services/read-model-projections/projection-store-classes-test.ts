import { ProjectionResult, ReadModelAction, UUID } from '@boostercloud/framework-types'

export class AnEntity {
  public constructor(
    readonly id: UUID,
    readonly someKey: UUID,
    readonly someArrayKey: Array<UUID>,
    readonly count: number
  ) {}

  public getPrefixedKey(prefix: string): string {
    return `${prefix}-${this.someKey}`
  }
}

export class SomeReadModel {
  public constructor(readonly id: UUID, readonly kind: string, readonly kindArray: string, readonly count: number) {}

  public static someObserver(entity: AnEntity, obj: SomeReadModel): ProjectionResult<SomeReadModel> {
    const count = (obj?.count || 0) + entity.count
    return new SomeReadModel(entity.someKey, 'some', '', count)
  }

  public static someObserverArray(
    entity: AnEntity,
    readModelID: UUID,
    obj: SomeReadModel
  ): ProjectionResult<SomeReadModel> {
    const count = (obj?.count || 0) + entity.count
    const id = readModelID ?? 'NEW'
    return new SomeReadModel(id, 'some', readModelID?.toString(), count)
  }

  public static projectQueryReadModel(
    entity: AnEntity,
    readModelID?: UUID,
    obj?: SomeReadModel
  ): ProjectionResult<SomeReadModel> {
    const count = (obj?.count || 0) + entity.count
    const id = readModelID ?? 'NEW_QUERY'
    return new SomeReadModel(id, 'some', '', count)
  }

  public getId(): UUID {
    return this.id
  }

  public static projectionThatReturnsNothing(
    entity: AnEntity,
    currentReadModel: SomeReadModel
  ): ProjectionResult<SomeReadModel> {
    return ReadModelAction.Nothing
  }

  public static projectionThatReturnsDelete(
    entity: AnEntity,
    currentReadModel: SomeReadModel
  ): ProjectionResult<SomeReadModel> {
    return ReadModelAction.Delete
  }
}
