import { Command } from '@boostercloud/framework-core'
import { CommandInput, Register, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'
import {beforeHookException, beforeHookMutationID, beforeHookQuantity, throwExceptionId} from '../constants'

@Command({
  authorize: 'all',
  beforeCommand: [ChangeCartItem.beforeFn, ChangeCartItem.beforeFnV2],
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public static beforeFn(input: CommandInput, currentUser?: UserEnvelope): CommandInput {
    if (input.cartId === beforeHookMutationID) {
      input.quantity = beforeHookQuantity
    } else if (input.cartId === throwExceptionId) {
      throw new Error(beforeHookException)
    }
    return input
  }

  public static beforeFnV2(input: CommandInput, currentUser?: UserEnvelope): CommandInput {
    if (input.cartId === beforeHookMutationID) {
      input.cartId += '-modified'
    }
    return input
  }

  public static async handle(command: ChangeCartItem, register: Register): Promise<void> {
    register.events(new CartItemChanged(command.cartId, command.productId, command.quantity))
  }
}
