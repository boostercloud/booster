import { Command, Booster } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { Admin } from '../roles'
import { Stock } from '../entities/stock'
import { StockMoved } from '../events/stock-moved'
import { ErrorEvent } from '../events/error-event'

@Command({
  authorize: [Admin],
})
export class MoveStock {
  public constructor(
    readonly productID: string,
    readonly origin: string,
    readonly destination: string,
    readonly quantity: number
  ) {}

  public async handle(register: Register): Promise<void> {
    const stock = await Booster.fetchEntitySnapshot(Stock, this.productID)
    if (this.enoughStock(stock)) {
      register.events(new StockMoved(this.productID, this.origin, this.destination, this.quantity))
    } else {
      register.events(
        new ErrorEvent('MoveStock-' + this.productID, 'There is not enough stock to perform this operation', this)
      )
    }
  }

  private enoughStock(stock?: Stock): boolean {
    if (this.origin == 'provider') return true
    if (!stock) return false
    const count = stock.warehouses[this.origin]
    if (count >= this.quantity) return true
    return false
  }
}
