import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarOfferAdded } from '../events/car-offer-added'

@Command({
  authorize: 'all',
})
export class AddPurchaseOffer {
  public constructor(readonly id: UUID, readonly name: string, readonly purchaseIds: Array<string>) {}

  public static async handle(command: AddPurchaseOffer, register: Register): Promise<void> {
    register.events(new CarOfferAdded(command.id, command.name, command.purchaseIds))
  }
}
