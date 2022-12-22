import { tryCatchPromise, Layer } from '@boostercloud/framework-types/dist/effect'
import { FileSystemService, FileSystemError } from '.'
import * as fs from 'fs'

const readDirectoryContents = (directoryPath: string) =>
  tryCatchPromise(
    () => fs.promises.readdir(directoryPath),
    (reason) =>
      new FileSystemError(
        new Error(`There were some issues reading the directory ${directoryPath}:

    ${reason}`)
      )
  )

export const LiveFileSystem = Layer.fromValue(FileSystemService)({ readDirectoryContents })
