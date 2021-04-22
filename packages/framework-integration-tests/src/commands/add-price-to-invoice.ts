import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { InvoicePriceAdded } from '../events/invoice-price-added'
import { Invoice } from '../entities/invoice'
import { InvoiceFinished } from '../events/invoice-finished'

@Command({
  authorize: 'all',
})
export class AddPriceToInvoice {
  public constructor(readonly id: UUID, readonly totalPrice: number, readonly invoiceFinished: boolean) {}

  public static async handle(command: AddPriceToInvoice, register: Register): Promise<void> {
    if (command.invoiceFinished) {
      const currentSnapshotDate = new Date()
      const previousSnapshotDate = new Date(
        new Date(currentSnapshotDate).setSeconds(currentSnapshotDate.getSeconds() - 1)
      )
      const newestSnapshot = await Booster.entity(Invoice, command.id) as Invoice
      const oldSnapshot = await Booster.entity(Invoice, command.id, previousSnapshotDate) as Invoice
      console.log('///// OLDEST SNAPSHOT /////')
      console.log(oldSnapshot)
      console.log('///// NEWEST SNAPSHOT /////')
      console.log(newestSnapshot)
      register.events(
        new InvoiceFinished(
          command.id,
          newestSnapshot?.totalPrice,
          oldSnapshot?.totalPrice,
          currentSnapshotDate.toISOString(),
          previousSnapshotDate.toISOString()
        )
      )
    } else {
      register.events(new InvoicePriceAdded(command.id, command.totalPrice))
    }
  }
}
