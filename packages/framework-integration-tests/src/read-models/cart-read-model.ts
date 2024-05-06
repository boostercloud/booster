import { Booster, NonExposed, Projects, ReadModel } from '@boostercloud/framework-core'
import {
  ProjectionResult,
  ReadModelInterface,
  ReadModelRequestEnvelope,
  UserEnvelope,
  UUID,
} from '@boostercloud/framework-types'
import { CartItem } from '../common/cart-item'
import { Address } from '../common/address'
import { Cart, MigratedCart } from '../entities/cart'
import { Payment } from '../entities/payment'
import { beforeHookException, projectionErrorCartId, projectionErrorCartMessage, throwExceptionId } from '../constants'
import { ProductReadModel } from './product-read-model'

@ReadModel({
  authorize: 'all',
  before: [CartReadModel.beforeFn, CartReadModel.beforeFnV2],
})
export class CartReadModel {
  @NonExposed
  private testProperty: number

  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    readonly checks: number,
    public shippingAddress?: Address,
    public payment?: Payment,
    public cartItemsIds?: Array<string>,
    @NonExposed readonly test?: number
  ) {
    this.testProperty = 1
    console.log(this.testProperty)
  }

  public getChecks(): number {
    return this.checks
  }

  public get cartItemsSize(): number | undefined {
    return this.cartItems ? this.cartItems.length : 0
  }

  public get myAddress(): Promise<Address> {
    return Promise.resolve(this.shippingAddress || new Address('', '', '', '', '', ''))
  }

  public get lastProduct(): Promise<ProductReadModel | undefined> {
    if (this.cartItemsSize === 0) {
      return Promise.resolve(undefined)
    }
    return Booster.readModel(ProductReadModel)
      .filter({
        id: { eq: this.cartItems.at(-1)?.productId },
      })
      .searchOne()
  }

  public static async beforeFn(
    request: ReadModelRequestEnvelope<ReadModelInterface>,
    currentUser?: UserEnvelope
  ): Promise<ReadModelRequestEnvelope<ReadModelInterface>> {
    const id = request?.key?.id
    if (id && id === throwExceptionId) throw new Error(beforeHookException)
    return request
  }

  public static async beforeFnV2(
    request: ReadModelRequestEnvelope<ReadModelInterface>
  ): Promise<ReadModelRequestEnvelope<ReadModelInterface>> {
    const id = request.key?.id || request.filters?.id?.eq
    console.log(`Running \`beforeFnV2\` with ID = ${id}, and request = ${JSON.stringify(request)}`)
    if (!id || id !== 'before-fn-test') return request
    const newId = id + '-modified'
    if (request.key?.id) {
      return {
        ...request,
        key: {
          id: newId,
        },
      }
    } else {
      return {
        ...request,
        filters: { id: { eq: newId } },
      }
    }
  }

  @Projects(Cart, 'id')
  @Projects(MigratedCart, 'id')
  public static updateWithCart(
    cart: Cart | MigratedCart,
    oldCartReadModel?: CartReadModel
  ): ProjectionResult<CartReadModel> {
    if (cart.id === projectionErrorCartId) {
      throw new Error(projectionErrorCartMessage)
    }
    const cartProductIds = cart?.cartItems.map((item) => item.productId as string)
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    cart.getId()
    if (oldCartReadModel) {
      oldCartReadModel.getChecks()
    }

    return new CartReadModel(
      cart.id,
      cart.cartItems,
      cart.checks,
      cart.shippingAddress,
      oldCartReadModel?.payment,
      cartProductIds
    )
  }

  @Projects(Payment, 'cartId')
  public static updateCartPaymentStatus(
    payment: Payment,
    oldCartReadModel?: CartReadModel
  ): ProjectionResult<CartReadModel> {
    if (!oldCartReadModel) {
      return new CartReadModel(payment.cartId, [], 0, undefined, payment)
    }

    return new CartReadModel(
      oldCartReadModel.id,
      oldCartReadModel.cartItems,
      oldCartReadModel.checks,
      oldCartReadModel.shippingAddress,
      payment
    )
  }
}
