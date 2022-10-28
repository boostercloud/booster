import { tryCatchPromise, Layer } from '@boostercloud/framework-types/dist/effect'
import { FileSystemError, FileSystemService } from '.'
import * as fs from 'fs'

const readDirectoryContents = (directoryPath: string) =>
  tryCatchPromise(
    () => fs.promises.readdir(directoryPath),
    (reason) => new FileSystemError(reason)
  )

export const LiveFileSystem = Layer.fromValue(FileSystemService)({ readDirectoryContents })
