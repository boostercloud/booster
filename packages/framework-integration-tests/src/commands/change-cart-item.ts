import { Command, NonExposed, Trace } from "@boostercloud/framework-core";
import { CommandInput, Register, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'
import {
  beforeHookException,
  beforeHookMutationID,
  beforeHookQuantity,
  commandHandlerBeforeErrorCartId,
  commandHandlerBeforeErrorCartMessage,
  commandHandlerErrorCartId,
  commandHandlerErrorCartMessage,
  commandHandlerErrorIgnoredCartId,
  throwExceptionId,
} from '../constants'

@Command({
  authorize: 'all',
  before: [ChangeCartItem.beforeFn, ChangeCartItem.beforeFnV2],
})
export class ChangeCartItem {
  public constructor(
    readonly cartId: UUID,
    readonly productId: UUID,
    readonly quantity: number,
    @NonExposed readonly test: number
  ) {}

  public static async beforeFn(input: CommandInput, currentUser?: UserEnvelope): Promise<CommandInput> {
    if (input.cartId === beforeHookMutationID) {
      input.quantity = beforeHookQuantity
    } else if (input.cartId === throwExceptionId) {
      throw new Error(beforeHookException)
    } else if (input.cartId === commandHandlerBeforeErrorCartId) {
      throw new Error(commandHandlerBeforeErrorCartMessage)
    }
    const result = await Promise.resolve()
    input.test = 1
    console.log(result)
    return input
  }

  public static async beforeFnV2(input: CommandInput, currentUser?: UserEnvelope): Promise<CommandInput> {
    if (input.cartId === beforeHookMutationID) {
      input.cartId += '-modified'
    }
    const result = await Promise.resolve()
    console.log(result)
    return input
  }

  @Trace('CHANGE_CART_ITEM_HANDLER')
  public static async handle(command: ChangeCartItem, register: Register): Promise<void> {
    if (command.cartId === commandHandlerErrorCartId || command.cartId === commandHandlerErrorIgnoredCartId) {
      throw new Error(commandHandlerErrorCartMessage)
    }
    register.events(new CartItemChanged(command.cartId, command.productId, command.quantity))
  }
}
