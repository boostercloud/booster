import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { InvoicePriceAdded } from '../events/invoice-price-added'
import { Invoice } from '../entities/invoice'
import { InvoiceFinished } from '../events/invoice-finished'

@Command({
  authorize: 'all',
})
export class AddPriceToInvoice {
  public constructor(
    readonly id: UUID,
    readonly totalPrice: number,
    readonly invoiceFinished: boolean,
    readonly createdAt: string
  ) {}

  public static async handle(command: AddPriceToInvoice, register: Register): Promise<void> {
    register.events(new InvoicePriceAdded(command.id, command.totalPrice, command.createdAt))
    if (command.invoiceFinished) {
      // Look for the first snapshot (5th position) and reduce 3 events to generate a new snapshot
      const previousSnapshotDate = new Date(command.createdAt)
      // I don't have much invoices so that's not a valid entity to fetch
      const newestSnapshot = await Booster.entity(Invoice, command.id) as Invoice
      const oldSnapshot = await Booster.entity(Invoice, command.id, previousSnapshotDate) as Invoice
      console.log('///// OLDEST SNAPSHOT /////')
      console.log(oldSnapshot)
      console.log('///// NEWEST SNAPSHOT /////')
      console.log(newestSnapshot)
      register.events(new InvoiceFinished(command.id, newestSnapshot, oldSnapshot))
    }
  }
}
