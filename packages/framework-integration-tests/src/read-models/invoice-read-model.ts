import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Invoice } from '../entities/invoice'

@ReadModel({
  authorize: 'all',
})
export class InvoiceReadModel {
  public constructor(
    public id: UUID,
    readonly totalPrice: number,
    readonly oldestInvoiceDate?: string,
    readonly latestInvoiceDate?: string,
    readonly latestInvoicePrice?: number,
    readonly oldInvoicePrice?: number
  ) {}

  @Projects(Invoice, 'id')
  public static finalState(invoice: Invoice): ProjectionResult<InvoiceReadModel> {
    return new InvoiceReadModel(
      invoice.id,
      invoice.totalPrice,
      invoice.oldestInvoiceDate,
      invoice.latestInvoiceDate,
      invoice.latestInvoicePrice,
      invoice.oldInvoicePrice
    )
  }
}
