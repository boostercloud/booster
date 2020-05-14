import { Entity } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Entity
export class PostWithFields {
  public constructor(
    public id: UUID,
    readonly title: string,
    readonly body: string,
  ) {}

}
