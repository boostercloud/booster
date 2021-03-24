import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class InvoiceFinished {
  public constructor(readonly id: UUID, readonly latestInvoice: object, readonly oldInvoice: object) {}

  public entityID(): UUID {
    return this.id
  }
}
