import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class FileAdded {
  public constructor(readonly s3uri: string, readonly filesize: number) {}

  public entityID(): UUID {
    return this.s3uri
  }
}
