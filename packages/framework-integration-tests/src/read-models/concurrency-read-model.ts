import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Concurrency } from '../entities/concurrency'

@ReadModel({
  authorize: 'all',
})
export class ConcurrencyReadModel {
  public constructor(readonly id: UUID) {}

  @Projects(Concurrency, 'id')
  public static persisted(
    concurrency: Concurrency,
    concurrencyReadModel?: ConcurrencyReadModel
  ): ProjectionResult<ConcurrencyReadModel> {
    return new ConcurrencyReadModel(concurrency.id)
  }
}
