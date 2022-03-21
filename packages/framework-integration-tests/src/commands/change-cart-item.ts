import { Command } from '@boostercloud/framework-core'
import { CommandInput, Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'
import {
  afterHookMutationID,
  beforeHookException,
  beforeHookMutationID,
  beforeHookQuantity,
  throwExceptionId,
} from '../constants'
import { CartChecked } from '../events/cart-checked'

@Command({
  authorize: 'all',
  before: [ChangeCartItem.beforeFn, ChangeCartItem.beforeFnV2],
  after: [ChangeCartItem.afterFn],
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public static async beforeFn(input: CommandInput, register: Register): Promise<CommandInput> {
    if (input.cartId === beforeHookMutationID) {
      input.quantity = beforeHookQuantity
    } else if (input.cartId === throwExceptionId) {
      throw new Error(beforeHookException)
    }
    const result = await Promise.resolve()
    console.log(result)
    return input
  }

  public static async beforeFnV2(input: CommandInput, register: Register): Promise<CommandInput> {
    if (input.cartId === beforeHookMutationID) {
      input.cartId += '-modified'
    }
    const result = await Promise.resolve()
    console.log(result)
    return input
  }

  public static async handle(command: ChangeCartItem, register: Register): Promise<void> {
    register.events(new CartItemChanged(command.cartId, command.productId, command.quantity))
  }

  public static async afterFn(previousResult: unknown, input: CommandInput, register: Register): Promise<void> {
    if (input.cartId === afterHookMutationID) {
      register.events(new CartChecked(input.cartId))
    }
  }
}
