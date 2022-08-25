import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Counter } from '../entities/counter'

@ReadModel({
  authorize: 'all',
})
export class CounterReadModel {
  public constructor(readonly id: UUID, readonly identifier: string, readonly amount: number) {}

  @Projects(Counter, 'id')
  public static updateCounter(
    counter: Counter,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _oldCounterReadModel?: CounterReadModel
  ): ProjectionResult<CounterReadModel> {
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    counter.getId()

    return new CounterReadModel(counter.id, counter.identifier, counter.amount)
  }
}
