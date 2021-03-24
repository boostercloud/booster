import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { InvoicePriceAdded } from '../events/invoice-price-added'
import { InvoiceFinished } from '../events/invoice-finished'

@Entity
export class Invoice {
  public constructor(
    public id: UUID,
    readonly totalPrice: number,
    readonly createdAt: string,
    readonly latestInvoice?: object,
    readonly oldInvoice?: object
  ) {}

  @Reduces(InvoicePriceAdded)
  public static addPriceToInvoice(event: InvoicePriceAdded, currentInvoice?: Invoice): Invoice {
    const newTotalPrice = currentInvoice?.totalPrice ? currentInvoice.totalPrice + event.totalPrice : event.totalPrice
    return new Invoice(event.id, newTotalPrice, event.createdAt)
  }

  @Reduces(InvoiceFinished)
  public static finishInvoiceProcess(event: InvoiceFinished, currentInvoice?: Invoice): Invoice {
    return new Invoice(
      event.id,
      currentInvoice?.totalPrice ?? 0,
      currentInvoice?.createdAt ?? new Date(10000).toISOString(),
      event.latestInvoice,
      event.oldInvoice
    )
  }
}
