import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { AnotherCounterAdded } from '../events/another-counter-added'

@Entity({
  authorizeReadEvents: 'all',
})
export class AnotherCounter {
  public constructor(readonly id: UUID, readonly identifier: string, public amount: number) {}

  public getId(): UUID {
    return this.id
  }

  @Reduces(AnotherCounterAdded)
  public static anotherCounterAdded(event: AnotherCounterAdded, currentAnotherCounter: AnotherCounter): AnotherCounter {
    if (currentAnotherCounter) {
      currentAnotherCounter.amount += 1
    } else {
      currentAnotherCounter = new AnotherCounter(event.anotherCounterId, event.identifier, 1)
    }
    return currentAnotherCounter
  }
}
