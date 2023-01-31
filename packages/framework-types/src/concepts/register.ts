import { EventInterface } from './event'
import { UserEnvelope, ContextEnvelope } from '../envelope'
import { UUID } from './uuid'
import { NotificationInterface } from './notification'

export type FlusherFunction = (record: Register) => Promise<void>

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
  public readonly eventList: Array<EventInterface | NotificationInterface> = []

  public constructor(
    readonly requestID: UUID,
    readonly responseHeaders: Record<string, string>,
    readonly flusher: FlusherFunction,
    readonly currentUser?: UserEnvelope,
    readonly context?: ContextEnvelope
  ) {}

  /**
   * Register a list of events to be added to the event-store on handler completion
   * @param events
   */
  public events(...events: Array<EventInterface>): Register {
    this.eventList.push(...events)
    return this
  }

  /**
   * Register a list of notifications to be added to the event-store on handler completion
   * @param notifications
   */
  public notifications(...notifications: Array<NotificationInterface>): Register {
    this.eventList.push(...notifications)
    return this
  }

  public async flush(): Promise<void> {
    await this.flusher(this)
    this.eventList.length = 0
  }
}
