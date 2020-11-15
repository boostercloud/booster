import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { PostCreated } from '../events/post-created'

@Entity
export class PostWithReducer {
  public constructor(
    public id: UUID,
    readonly title: string,
    readonly body: string,
  ) {}

  @Reduces(PostCreated)
  public static reducePostCreated(event: PostCreated, currentPostWithReducer?: PostWithReducer): PostWithReducer {
    return /* NEW PostWithReducer HERE */
  }

}
