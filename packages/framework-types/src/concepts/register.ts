import { EventInterface } from './event'
import { UserEnvelope } from '../envelope'
import { UUID } from './uuid'

/**
 * Object passed by booster to handlers to accumulate the events emmited or the commands submitted by the handler.
 * All the events and commands registered won't be stored until the handler has finalized.
 *
 * Example:
 * ```typescript
 *  public handle(register: Register): void {
 *    //...
 *    register.events(new CartItemChanged(cart, this.sku, this.quantity))
 *    if (this.hasPromotion()) {
 *      if (this.isEmpty()) {
 *        throw new Error("Added a promotion to an empty cart")
 *      } else {
 *        register.commands(new ApplyPromoToCart(this.promo, cart)
 *      }
 *    }
 *    // ...
 *  }
 * ```
 */
export class Register {
  public readonly eventList: Array<EventInterface> = []

  public constructor(readonly requestID: UUID, readonly currentUser?: UserEnvelope) {}

  /**
   * Register a list of events to be added to the event-store on handler completion
   * @param events
   */
  public events(...events: Array<EventInterface>): Register {
    this.eventList.push(...events)
    return this
  }
}
