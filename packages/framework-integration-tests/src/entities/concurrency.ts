import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { ConcurrencyPersisted } from '../events/concurrency-persisted'

@Entity({
  authorizeReadEvents: 'all',
})
export class Concurrency {
  public constructor(readonly id: UUID, readonly otherId: UUID) {}

  public getId() {
    return this.id
  }

  @Reduces(ConcurrencyPersisted)
  public static persisted(event: ConcurrencyPersisted, currentConcurrency: Concurrency): Concurrency {
    return new Concurrency(event.id, event.otherId)
  }
}
