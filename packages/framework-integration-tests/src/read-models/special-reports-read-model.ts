import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { Product } from '../entities/product'

@ReadModel({
  authorize: async (currentUser?: UserEnvelope): Promise<void> => {
    if (currentUser?.claims['specialReportAccess'] !== 'true') {
      return Promise.reject('You are not allowed to see such insightful report')
    }
    return Promise.resolve()
  },
})
export class SpecialReportsReadModel {
  public constructor(readonly id: UUID, readonly luck: number) {}

  @Projects(Product, 'id')
  public static updateCounter(
    product: Product,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _oldCounterReadModel?: SpecialReportsReadModel
  ): ProjectionResult<SpecialReportsReadModel> {
    const luck = product.description.length + product.displayName.length + Math.random()
    return new SpecialReportsReadModel(product.id, luck)
  }
}
