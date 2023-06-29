import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Concurrency } from '../entities/concurrency'

@ReadModel({
  authorize: 'all',
})
export class OtherConcurrencyReadModel {
  public constructor(readonly id: UUID, readonly otherId: UUID) {}

  @Projects(Concurrency, 'id')
  public static persisted(
    concurrency: Concurrency,
    concurrencyReadModel?: OtherConcurrencyReadModel
  ): ProjectionResult<OtherConcurrencyReadModel> {
    return new OtherConcurrencyReadModel(concurrency.id, concurrency.otherId)
  }

  @Projects(Concurrency, 'otherId')
  public static persistedByOtherId(
    concurrency: Concurrency,
    concurrencyReadModel?: OtherConcurrencyReadModel
  ): ProjectionResult<OtherConcurrencyReadModel> {
    return new OtherConcurrencyReadModel(concurrency.otherId, concurrency.otherId)
  }
}
