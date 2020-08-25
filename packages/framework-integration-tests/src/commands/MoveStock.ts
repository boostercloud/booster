import { Command, Booster } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { Admin } from '../roles'
import { Stock } from '../entities/Stock'
import { StockMoved } from '../events/StockMoved'
import { ErrorEvent } from '../events/ErrorEvent'

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
        new ErrorEvent('MoveStock-' + this.productID, 'There is not enough stock of this product to perform this operation', this)
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
