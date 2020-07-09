import { Command } from '@boostercloud/framework-core'
import { ProductCreated } from '../events/ProductCreated'
import { Register, UUID } from '@boostercloud/framework-types'
import { User, Admin } from '../roles'
import { SKU } from '../common/sku'

@Command({
  authorize: [Admin, User],
})
export class CreateProduct {
  public constructor(
    readonly sku: SKU,
    readonly displayName: string,
    readonly description: string,
    readonly priceInCents: number,
    readonly currency: string
  ) {}

  public async handle(register: Register): Promise<void> {
    register.events(
      new ProductCreated(UUID.generate(), this.sku, this.displayName, this.description, {
        cents: this.priceInCents,
        currency: this.currency,
      })
    )
  }
}
