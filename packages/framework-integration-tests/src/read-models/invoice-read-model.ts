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
    readonly createdAt: string,
    readonly latestInvoice?: object,
    readonly oldInvoice?: object
  ) {}

  @Projects(Invoice, 'id')
  public static finalState(invoice: Invoice): ProjectionResult<InvoiceReadModel> {
    return new InvoiceReadModel(
      invoice.id,
      invoice.totalPrice,
      invoice.createdAt,
      invoice.latestInvoice,
      invoice.oldInvoice
    )
  }
}
