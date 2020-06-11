import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'

@Command({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone 
})
export class ChangeCartWithFields {
  public constructor(
    readonly cartId: UUID,
    readonly sku: string,
    readonly quantity: number,
  ) {}

  public async handle(register: Register): Promise<void> {
    register.events( /* YOUR EVENT HERE */)
  }
}
