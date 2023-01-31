import { Register } from '@boostercloud/framework-types'
import { EventHandler } from '@boostercloud/framework-core'
import { CartAbandoned } from '../notifications/cart-abandoned'

@EventHandler(CartAbandoned)
export class HandleCartAbandoned {
  public static async handle(event: CartAbandoned, register: Register): Promise<void> {
    console.log('CART ABANDONED!!!', JSON.stringify(event))
  }
}
