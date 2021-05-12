import { StockMoved } from '../events/stock-moved'
import { Register } from '@boostercloud/framework-types'
import { ProductAvailabilityChanged } from '../events/product-availability-changed'
import { Booster, EventHandler } from '@boostercloud/framework-core'
import { Product } from '../entities/product'

@EventHandler(StockMoved)
export class HandleAvailability {
  public static async handle(event: StockMoved, register: Register): Promise<void> {
    const product = await Booster.entity(Product, event.productID)
    if (!product) {
      // This means that we have moved stock of a product we don't have in our store. Ignore it
      return
    }
    if (event.origin == 'provider') {
      // New stock enters the system
      register.events(new ProductAvailabilityChanged(event.productID, event.quantity))
    } else if (event.destination == 'customer') {
      // Stock goes to the customer
      register.events(new ProductAvailabilityChanged(event.productID, -event.quantity))
    }
    // In terms of availability, it doesn't matter in which warehouse the stock is as soon as there's stock
  }
}
