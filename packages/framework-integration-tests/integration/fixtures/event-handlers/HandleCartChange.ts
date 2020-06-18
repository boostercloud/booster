import { CartItemChanged } from '../events/CartItemChanged'
import { EventHandler } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@EventHandler(CartItemChanged)
export class HandleCartChange {
  public static async handle(event: CartItemChanged, register: Register): Promise<void> {}
}
