import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Projects, ReadModel } from '@boostercloud/framework-core'
import { Admin } from '../roles'
import { Stock } from '../entities/stock'

@ReadModel({
  authorize: [Admin]
})
export class StockReadModel {
  public constructor(readonly id: UUID, readonly warehouses: Record<string, number>) {
  }

  @Projects(Stock, 'id')
  public static projectStock(entity: Stock): ProjectionResult<StockReadModel> {
    return new StockReadModel(entity.id, entity.warehouses)
  }
}
