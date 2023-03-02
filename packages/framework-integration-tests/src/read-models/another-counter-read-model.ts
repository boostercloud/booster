import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { AnotherCounter } from '../entities/another-counter'

@ReadModel({
  authorize: 'all',
})
export class AnotherCounterReadModel {
  public constructor(readonly id: UUID, readonly identifier: string, readonly amount: number) {}

  @Projects(AnotherCounter, 'id')
  public static updateAnotherCounter(
    anotherCounter: AnotherCounter,
    oldAnotherCounterReadModel?: AnotherCounterReadModel
  ): ProjectionResult<AnotherCounterReadModel> {
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    anotherCounter.getId()

    return new AnotherCounterReadModel(anotherCounter.id, anotherCounter.identifier, anotherCounter.amount)
  }
}
