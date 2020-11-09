import { ScheduledCommand } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { CartChecked } from '../events/cart-checked'

@ScheduledCommand({
  minute: '0/1',
})
export class CheckCartCount {
  public static async handle(register: Register): Promise<void> {
    // We would normally generate a UUID here, but using a static ID
    // simplifies the query in the integration tests.
    register.events(new CartChecked('the-checked-cart'))
  }
}
