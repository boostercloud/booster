import { Projects, ReadModel } from '@boostercloud/framework-core'
import {
  ProjectionInfo,
  ProjectionInfoReason,
  ProjectionResult,
  ReadModelAction,
  UserEnvelope,
  UUID,
} from '@boostercloud/framework-types'
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

  @Projects(Product, 'id', SpecialReportsReadModel.updateCounter)
  public static updateCounter(
    product: Product,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _oldCounterReadModel?: SpecialReportsReadModel,
    projectionInfo?: ProjectionInfo
  ): ProjectionResult<SpecialReportsReadModel> {
    if (projectionInfo?.reason === ProjectionInfoReason.ENTITY_DELETED) {
      return ReadModelAction.Delete
    }
    const luck = product.description.length + product.displayName.length + Math.random()
    return new SpecialReportsReadModel(product.id, luck)
  }
}
