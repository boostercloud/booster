import { Query } from '@boostercloud/framework-core'
import { Admin, UserWithEmail } from '../roles'

@Query({
  authorize: [Admin, UserWithEmail],
})
export class CartWithRole {
  public constructor() {}

  public static async handle(query: CartWithRole): Promise<boolean> {
    return true
  }
}
