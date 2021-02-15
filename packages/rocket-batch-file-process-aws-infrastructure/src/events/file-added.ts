import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class FileAdded {
  public constructor(readonly fileURI: string, readonly filesize: number) {}

  public entityID(): UUID {
    return this.fileURI
  }
}
