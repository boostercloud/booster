import { ScheduledCommand } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { ProductCreated } from '../events/product-created'

@ScheduledCommand({
  minute: '0/5',
})
export class CheckCartCount {
  public static async handle(register: Register): Promise<void> {
    register.events(
      new ProductCreated(UUID.generate(), 'scheduled-product-created', 'scheduledProduct', 'A scheduled product', {
        cents: 1000,
        currency: 'EUR',
      })
    )
  }
}
