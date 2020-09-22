import { ReadModel } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@ReadModel({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone
})
export class CartReadModel {
  public constructor(
    public id: UUID,
  ) {}

}
