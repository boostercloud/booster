import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CounterAdded } from '../events/counter-added'

@Entity({
  authorizeReadEvents: 'all',
})
export class Counter {
  public constructor(readonly id: UUID, readonly identifier: string, public amount: number) {}

  public getId(): UUID {
    return this.id
  }

  @Reduces(CounterAdded)
  public static counterAdded(event: CounterAdded, currentCounter: Counter): Counter {
    if (currentCounter) {
      currentCounter.amount += 1
    } else {
      currentCounter = new Counter(event.counterId, event.identifier, 1)
    }
    return currentCounter
  }
}
