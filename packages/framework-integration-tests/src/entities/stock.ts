import { Entity, Reduces } from '@boostercloud/framework-core'
import { UserEnvelope, UUID } from '@boostercloud/framework-types'
import { StockMoved } from '../events/stock-moved'

@Entity({
  authorizeReadEvents: async (currentUser?: UserEnvelope): Promise<void> => {
    if (currentUser?.claims['magicWord'] === 'opensesame') {
      return Promise.resolve()
    }
    return Promise.reject("You don't know the magic word!")
  },
})
export class Stock {
  public constructor(readonly id: UUID, readonly warehouses: Record<string, number>) {}

  @Reduces(StockMoved)
  public static stockMoved(event: StockMoved, currentStock?: Stock): Stock {
    if (currentStock == null) {
      const stock = new Stock(event.productID, {})
      stock.warehouses[event.origin] = 0 // It must come from a provider or a different dimension
      stock.warehouses[event.destination] = event.quantity
      return stock
    } else {
      currentStock.warehouses[event.origin] -= event.quantity
      currentStock.warehouses[event.destination] += event.quantity
      return currentStock
    }
  }
}
