import { EventInterface } from './event'
import { CommandInterface } from './command'
import { UserEnvelope } from '../envelope'

/**
 * Object passed by booster to handlers to accumulate the events emmited or the commands submitted by the handler.
 * All the events and commands registered won't be stored until the handler has finalized.
 *
 * Example:
 * ```typescript
 *  public handle(register: Register): void {
 *    //...
 *    register.events(new ChangedCartItem(cart, this.sku, this.quantity))
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
  public readonly commandList: Array<CommandInterface> = []

  public constructor(readonly requestID: string, readonly currentUser?: UserEnvelope) {}

  /**
   * Register a list of events to be added to the event-store on handler completion
   * @param events
   */
  public events(...events: Array<EventInterface>): Register {
    this.eventList.push(...events)
    return this
  }

  /**
   * Register a list of commands to be submitted on handler completion
   * @param commands
   */
  public commands(...commands: Array<CommandInterface>): Register {
    this.commandList.push(...commands)
    return this
  }
}
