import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { PackCreated } from '../events/pack-created'

/**
 * A pack object represents a group of products that works great together
 */
@Entity
export class Pack {
  public constructor(readonly id: UUID, readonly name: string, readonly products: Array<UUID>) {}

  @Reduces(PackCreated)
  public static createOrder(event: PackCreated): Pack {
    return new Pack(event.packID, event.name, event.products)
  }
}
