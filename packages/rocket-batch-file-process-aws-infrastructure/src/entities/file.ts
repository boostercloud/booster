import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { FileAdded } from '../events/file-added'

@Entity
export class File {
  public constructor(public id: UUID, readonly filesize: number) {}

  @Reduces(FileAdded)
  public static reduceSmallFileAdded(event: FileAdded, currentSmallFile?: File): File {
    return new File(event.fileURI, event.filesize)
  }
}
