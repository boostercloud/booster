import { Entity } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Entity
export class Post {
  public constructor(
    public id: UUID,
  ) {}

}
