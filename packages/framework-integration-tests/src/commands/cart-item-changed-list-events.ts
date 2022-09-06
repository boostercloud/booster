/* eslint-disable @typescript-eslint/no-unused-vars */
import { Booster, Command } from '@boostercloud/framework-core'
import {
  EventSearchRequestArgs,
  FilteredEventEnvelope,
  FilterFor,
  PaginatedEventSearchResponse,
  Register,
  SortFor,
} from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'

@Command({
  authorize: 'all',
})
export class CartItemChangedListEvents {
  constructor(
    readonly by: 'entityTypeNameAndId' | 'createdAtAndQuantity' | 'cartIdAndQuantity',
    readonly entityTypeName?: string,
    readonly entityID?: string,
    readonly createdAt?: string,
    readonly cartId?: string,
    readonly quantity?: number
  ) {}

  public static async handle(
    command: CartItemChangedListEvents,
    register: Register
  ): Promise<Array<PaginatedEventSearchResponse>> {
    const filter: FilterFor<FilteredEventEnvelope<CartItemChanged>> = this.buildFilter(command)
    const sort: SortFor<FilteredEventEnvelope<CartItemChanged>> = this.buildSort(command)
    let cursor: Record<string, string> | undefined = undefined
    let count = 0
    const results: Array<PaginatedEventSearchResponse> = []

    do {
      const request: EventSearchRequestArgs<CartItemChanged> = {
        filter: filter,
        sortBy: sort,
        limit: 10,
        afterCursor: cursor,
      }
      const paginatedEventSearchResponse: PaginatedEventSearchResponse = await Booster.filteredEvents(request)
      count = paginatedEventSearchResponse.count ?? 0
      cursor = paginatedEventSearchResponse.cursor
      results.push(paginatedEventSearchResponse)
    } while (count !== 0 && cursor !== undefined)
    return results
  }

  private static buildFilter(command: CartItemChangedListEvents): FilterFor<FilteredEventEnvelope<CartItemChanged>> {
    switch (command.by) {
      case 'cartIdAndQuantity':
        return {
          value: {
            cartId: {
              eq: command.cartId,
            },
            quantity: {
              eq: command.quantity,
            },
          },
        }
      case 'createdAtAndQuantity':
        return {
          createdAt: {
            gte: command.createdAt,
          },
          value: {
            cartId: {
              eq: command.cartId,
            },
            quantity: {
              isDefined: true,
            },
          },
        }
      case 'entityTypeNameAndId':
        return {
          entityTypeName: {
            eq: command.entityTypeName,
          },
          entityID: {
            eq: command.entityID,
          },
        }
    }
  }

  private static buildSort(command: CartItemChangedListEvents): SortFor<FilteredEventEnvelope<CartItemChanged>> {
    switch (command.by) {
      case 'cartIdAndQuantity':
        return {
          value: {
            productId: 'DESC',
          },
        }
      case 'createdAtAndQuantity':
        return {
          createdAt: 'DESC',
        }
      case 'entityTypeNameAndId':
        return {
          entityID: 'DESC',
        }
    }
  }
}
